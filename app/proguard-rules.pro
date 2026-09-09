# 접근성 서비스·액티비티·서비스는 매니페스트에서 이름으로 참조되므로 유지
-keep class kr.manggome.touchnotch.service.NotchAccessibilityService { *; }
-keep class kr.manggome.touchnotch.record.ScreenRecordService { *; }
-keep class kr.manggome.touchnotch.record.ScreenCaptureRequestActivity { *; }
-keep class kr.manggome.touchnotch.ui.MainActivity { *; }
-keep class kr.manggome.touchnotch.TouchNotchApp { *; }
-keep class kr.manggome.touchnotch.BuildConfig { *; }

# 설정 값은 enum key 문자열로 저장하므로 enum 을 유지한다
-keepclassmembers enum kr.manggome.touchnotch.model.** { *; }

-dontwarn org.jetbrains.annotations.**
