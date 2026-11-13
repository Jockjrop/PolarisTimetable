package com.polaris.timetable.sharing;

import com.journeyapps.barcodescanner.CaptureActivity;

/**
 * 竖屏扫码界面。
 *
 * <p>zxing 自带的 {@code CaptureActivity} 在 manifest 里声明为 {@code sensorLandscape}，
 * 竖屏持机时画面会横过来，取景框和提示都错位。这里继承它并在 manifest 中锁定 portrait，
 * 其余行为（解码、权限、返回）沿用父类。
 */
public class PolarisScanActivity extends CaptureActivity {
}
