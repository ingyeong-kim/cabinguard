package ai.cabinguard.app.signal;

/**
 * 앱이 구독하는 차량 신호의 현재 값.
 * 실제 차량(AAOS)에서는 CarApiSource가, 차량이 없는 환경에서는 화면의 시뮬레이션 버튼이 값을 바꾼다.
 * 값이 바뀌면 Listener로 알려 주며, 화면과 알림 로직은 신호의 출처를 구분하지 않는다.
 */
public final class VehicleSignals {

    public interface Listener {
        void onSignalsChanged(VehicleSignals signals);
    }

    private boolean ignitionOn = true;
    private boolean gearPark = true;
    private boolean parkingBrakeOn = true;
    private boolean driverDoorOpen = false;
    private double outsideTempC = 34.0;
    private Listener listener;

    public void setListener(Listener listener) { this.listener = listener; }

    public boolean isIgnitionOn() { return ignitionOn; }
    public boolean isGearPark() { return gearPark; }
    public boolean isParkingBrakeOn() { return parkingBrakeOn; }
    public boolean isDriverDoorOpen() { return driverDoorOpen; }
    public double getOutsideTempC() { return outsideTempC; }

    /** P단이면서 주차브레이크가 걸린 상태. 하차 직전으로 본다. */
    public boolean isParked() { return gearPark && parkingBrakeOn; }

    public void setIgnitionOn(boolean value) { if (ignitionOn != value) { ignitionOn = value; notifyChanged(); } }
    public void setGearPark(boolean value) { if (gearPark != value) { gearPark = value; notifyChanged(); } }
    public void setParkingBrakeOn(boolean value) { if (parkingBrakeOn != value) { parkingBrakeOn = value; notifyChanged(); } }
    public void setDriverDoorOpen(boolean value) { if (driverDoorOpen != value) { driverDoorOpen = value; notifyChanged(); } }
    public void setOutsideTempC(double value) { if (outsideTempC != value) { outsideTempC = value; notifyChanged(); } }

    private void notifyChanged() {
        if (listener != null) listener.onSignalsChanged(this);
    }
}
