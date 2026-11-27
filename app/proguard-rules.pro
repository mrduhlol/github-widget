# Keep widget providers instantiated by Android's AppWidgetManager
-keep class com.example.githubwidget.*WidgetProvider { *; }

# Keep WorkManager CoroutineWorker subclasses instantiated by reflection
-keep class com.example.githubwidget.widget.*Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
