package ai.cabinguard.app.signal;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * AAOS Car API(CarPropertyManager)로 실제 차량 신호를 구독한다.
 *
 * Pleos Connect SDK 정책(AAOS Alignment)에 따라 기어, 외기온도, 시동처럼 AAOS 표준에 있는 데이터는
 * 표준 Car API로 받는다. 운전석 문 열림과 실내 온도는 일반 앱에 열려 있는 표준 속성이 없어
 * 2차 개발에서 Pleos Vehicle SDK(Door, HVAC)로 연결한다.
 *
 * 이 클래스는 android.car 라이브러리가 있는 기기에서만 로드해야 하므로 MainActivity가
 * FEATURE_AUTOMOTIVE 확인 후에만 생성한다.
 */
public final class CarApiSource {

    /** android.car.VehicleIgnitionState.ON */
    private static final int IGNITION_STATE_ON = 4;
    /** android.car.VehicleIgnitionState.START */
    private static final int IGNITION_STATE_START = 5;

    private final VehicleSignals signals;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Car car;
    private CarPropertyManager propertyManager;

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(final CarPropertyValue value) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            apply(value);
                        }
                    });
                }

                @Override
                public void onErrorEvent(int propertyId, int zone) {
                    // 값을 못 받은 속성은 직전 값을 유지한다.
                }
            };

    public CarApiSource(VehicleSignals signals) {
        this.signals = signals;
    }

    /** @return 차량 신호 구독에 성공하면 true */
    public boolean connect(Context context) {
        try {
            car = Car.createCar(context);
            if (car == null) return false;
            propertyManager = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            if (propertyManager == null) return false;
            int registered = 0;
            registered += register(VehiclePropertyIds.GEAR_SELECTION);
            registered += register(VehiclePropertyIds.PARKING_BRAKE_ON);
            registered += register(VehiclePropertyIds.IGNITION_STATE);
            registered += register(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE);
            return registered > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private int register(int propertyId) {
        try {
            boolean ok = propertyManager.registerCallback(callback, propertyId, CarPropertyManager.SENSOR_RATE_ONCHANGE);
            return ok ? 1 : 0;
        } catch (Throwable t) {
            // 차종에 따라 지원하지 않는 속성이 있을 수 있다. 해당 신호만 건너뛴다.
            return 0;
        }
    }

    private void apply(CarPropertyValue value) {
        if (value == null || value.getValue() == null) return;
        Object v = value.getValue();
        int id = value.getPropertyId();
        if (id == VehiclePropertyIds.GEAR_SELECTION && v instanceof Integer) {
            signals.setGearPark(((Integer) v) == VehicleGear.GEAR_PARK);
        } else if (id == VehiclePropertyIds.PARKING_BRAKE_ON && v instanceof Boolean) {
            signals.setParkingBrakeOn((Boolean) v);
        } else if (id == VehiclePropertyIds.IGNITION_STATE && v instanceof Integer) {
            int state = (Integer) v;
            signals.setIgnitionOn(state == IGNITION_STATE_ON || state == IGNITION_STATE_START);
        } else if (id == VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE && v instanceof Float) {
            signals.setOutsideTempC(Math.round((Float) v));
        }
    }

    public void disconnect() {
        try {
            if (propertyManager != null) propertyManager.unregisterCallback(callback);
            if (car != null) car.disconnect();
        } catch (Throwable ignored) {
        }
    }
}
