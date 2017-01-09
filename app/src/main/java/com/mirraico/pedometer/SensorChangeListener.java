package com.mirraico.pedometer;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class SensorChangeListener implements SensorEventListener {

    //存放三轴数据
    private float[] oriValues = new float[3];

    //是否上升
    private boolean isUp = false;
    //上一点的状态，是否上升
    private boolean lastIsUp = false;
    //持续上升次数
    private int continueUpCount = 0;
    //上一点的持续上升次数，为了记录波峰的上升次数(在下降时使用)
    private int lastContinueUpCount = 0;
    //波峰值
    private float peakValue = 0;
    //波谷值
    private float valleyValue = 0;
    //此次波峰的时间
    private long timeOfThisPeak = 0;
    //上次波峰的时间
    private long timeOfLastPeak = 0;
    //当前的时间
    private long timeOfNow = 0;
    //当前传感器的值
    private float sensorValue = 0;
    //上次传感器的值
    private float lastSensorValue = 0;
    //峰谷阀值，该值动态计算，大于该值才记为一步，初始2.0
    private float thresholdValue = (float) 2.0;
    //能够纳入峰谷阀值计算的阀值，大于该值的峰谷差值才参与上述峰谷阀值的计算
    private final float judgeValue = (float) 1.3;
    //用于存放计算峰谷阀值的峰谷差值
    private final int arrayNum = 4;
    private float[] thresholdArray = new float[arrayNum];
    private int arrayCount = 0;
    //步数累计，用于过滤无意义抖动
    private int stepCount = 0;

    //回调接口
    private StepListener stepListener;

    //构造函数，传递回调接口进来
    public SensorChangeListener(StepListener stepListener) {
        this.stepListener = stepListener;
    }

    /*
     * 原理
     * 1.波峰波谷的判定(连续上升、上升阀值等)
     * 2.峰谷差值需要大于峰谷阀值
     * 3.波峰时间差需要大于某个值
     * 4.峰谷差值是动态改变的
     *
     *
     * 可以调节的参数
     * 1.波峰判定中的连续上升次数lastContinueUpCount  2
     * 2.波峰判定中，没达到连续上升次数但可能是一次很大的上升值，判定该上升值的阀值  20
     * 3.波峰时间差判定  250ms
     * 4.计步累计启动步数  5
     * 5.参与峰谷阀值计算的阀值judgeValue  1.3
     * 6.重新计算峰谷阀值的峰谷差值累计个数arrayNum  4
     * 7.梯度化阀值中的梯度值和阀值设定averageValue()
     *   多判断了就尝试提高阀值，少判断了就尝试降低阀值
         测试走路、跑步、手机位置等多种场景，设定好不同场景的梯度，再设定阀值
     */



    /*
     * 注册服务后一直会调用这个函数(sensor是一直会变化的)
     * 先对三轴数据进行平方和开根号的处理
     * 然后调用DetectorNewStep检测步子
     * 检测到步子后会回调onStep()函数
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        for (int i = 0; i < 3; i++) {
            oriValues[i] = event.values[i];
        }
        sensorValue = (float) Math.sqrt(oriValues[0] * oriValues[0]
                + oriValues[1] * oriValues[1] + oriValues[2] * oriValues[2]);

        detectNewStep(sensorValue);
    }

    /*
     * 检测步子，并开始计步
     * 1.传入sersor中的数据
     * 2.如果检测到了波峰，并且符合时间差以及阈值的条件，则判定为1步
     * 3.符合时间差条件，波峰波谷差值大于initialValue，则将该差值纳入阈值的计算中
     */
    public void detectNewStep(float sensorValue) {
        if (lastSensorValue == 0) { //第一次值
            lastSensorValue = sensorValue;
        } else {
            if (detectPeak(sensorValue, lastSensorValue)) { //如果检测到了波峰
                timeOfLastPeak = timeOfThisPeak;
                timeOfNow = System.currentTimeMillis();
                if (timeOfNow - timeOfLastPeak >= 250 //过滤短时间的连续波峰
                        && (peakValue - valleyValue >= thresholdValue) //峰谷阀值判定，峰谷差值大于该阀值才有效
                        ) {
                    timeOfThisPeak = timeOfNow;

                    //超过5步开始计步，防止无意义抖动，前5步难得计算了(因为项目中不关注这几步)，想要算的在广播中传值就行了
                    if (detectValidStep()) {
                        //调用回调函数
                        if (stepListener != null) {
                            stepListener.onStep();
                        }
                    }
                }
                if (timeOfNow - timeOfLastPeak >= 250
                        && (peakValue - valleyValue >= judgeValue) //参与峰谷阀值计算的阀值，峰谷差值大于该阀值才参与峰谷阀值的计算
                        ) {
                    timeOfThisPeak = timeOfNow; //防止onStep()可能存在的耗时操作，重新修正下时间
                    thresholdValue = calculateThreshold(peakValue - valleyValue); //峰谷阀值计算
                }
            }
        }
        lastSensorValue = sensorValue;
    }

    /*
     * 检测波峰
     * 以下四个条件判断为波峰：
     * 1.目前点为下降的趋势：isDirectionUp为false
     * 2.之前的点为上升的趋势：lastStatus为true
     * 3.到波峰为止，持续上升大于等于2次
     * 4.波峰值大于20
     * 记录波谷值
     * 1.观察波形图，可以发现在出现步子的地方，波谷的下一个就是波峰，有比较明显的特征以及差值
     * 2.所以要记录每次的波谷值，为了和下次的波峰做对比
     */
    public boolean detectPeak(float newValue, float oldValue) {
        lastIsUp = isUp; //记录上次状态
        if (newValue >= oldValue) { //本次上升
            isUp = true;
            continueUpCount++;
        } else { //本次下降
            lastContinueUpCount = continueUpCount;
            isUp = false;
            continueUpCount = 0;
        }

        if (!isUp && lastIsUp //波峰判定
                && (lastContinueUpCount >= 2 || oldValue >= 20) //防止抖动，持续上升了几次或一次上升了很大 才算作有效波峰
                ) {
            peakValue = oldValue; //波峰值记录
            return true; //该点是一个波峰
        } else if (!lastIsUp && isUp) { //波谷判定
            valleyValue = oldValue; //波谷值记录
            return false;
        } else { //正在持续上升或下降
            return false;
        }
    }

    /*
     * 更新界面的处理，不涉及到算法
     * 一般在通知更新界面之前，增加下面处理，为了处理无效运动：
     * 1.连续记录5才开始计步
     * 2.例如记录的4步用户停住超过3秒，则前面的记录失效，下次从头开始
     * 3.连续记录了4步用户还在运动，之前的数据才有效
     */
    private boolean detectValidStep() {
        boolean valid = false;
        if (timeOfThisPeak - timeOfLastPeak < 3000) {
            stepCount++;
            valid = stepCount >= 5;
        } else {
            stepCount = 0;
        }
        return valid;
    }

    /*
     * 阈值的计算
     * 1.通过合法的峰谷差值计算阈值
     * 2.记录4个值，存入tempValue[]数组中，累计4个值后进行计算
     * 3.在将数组传入函数averageValue中计算阈值
     */
    public float calculateThreshold(float value) {
        if (arrayCount < arrayNum) {
            thresholdArray[arrayCount++] = value;
            return thresholdValue; //刚开始的前4个累积过程中阀值不变
        } else {
            float newThreshold;
            newThreshold = averageValue(thresholdArray, arrayNum); //计算新阀值
            //计算用过的差值前移(不清空)
            for (int i = 1; i < arrayNum; i++) {
                thresholdArray[i-1] = thresholdArray[i];
            }
            thresholdArray[arrayNum-1] = value; //刚开始4个累计后，数组就永远是满的了，新的就直接放到数组最后
            return newThreshold;
        }
    }

    /*
     * 梯度化阈值
     * 1.计算数组的均值
     * 2.通过均值将阈值梯度化在一个范围里
     */
    public float averageValue(float value[], int n) {
        float ave = 0;
        for (int i = 0; i < n; i++) {
            ave += value[i];
        }
        ave = ave / arrayNum;

        //梯度化阀值的参数，还需要再调整
        //多判断了就尝试提高阀值，少判断了就尝试降低阀值
        //测试走路、跑步多场景，用梯度设定阀值
        if (ave >= 8)
            ave = (float) 4.3;
        else if (ave >= 7 && ave < 8)
            ave = (float) 3.3;
        else if (ave >= 4 && ave < 7)
            ave = (float) 2.3;
        else if (ave >= 3 && ave < 4)
            ave = (float) 2.0;
        else {
            ave = (float) 1.3;
        }
        return ave;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
