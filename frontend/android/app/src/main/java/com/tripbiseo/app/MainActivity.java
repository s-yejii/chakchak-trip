package com.tripbiseo.app;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

// 앱의 화면 하나(Activity). BridgeActivity가 Capacitor의 웹 화면(WebView)을 띄워 줘요
public class MainActivity extends BridgeActivity {

    // 앱 화면이 앞에 올 때마다 불려요 (처음 켤 때, 권한 창이 닫힐 때, 다른 앱에서 돌아올 때).
    // 그때마다 진짜 상태바와 아래 버튼 줄을 숨겨요. 처음 한 번만 숨기면 권한 창이 뜬 뒤에 다시 나와서요
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        // WindowInsetsControllerCompat: 상태바·버튼 줄을 보이거나 숨기는 안드로이드 도구
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        // 화면 끝을 쓸면 잠깐 나왔다가 스스로 다시 숨게
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.systemBars());
    }
}
