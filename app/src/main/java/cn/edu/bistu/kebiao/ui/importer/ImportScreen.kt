package cn.edu.bistu.kebiao.ui.importer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.edu.bistu.kebiao.BuildConfig
import cn.edu.bistu.kebiao.domain.WeekPattern
import cn.edu.bistu.kebiao.importer.AllowedHostPolicy
import cn.edu.bistu.kebiao.importer.BistuPageExtractor

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onImported()
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 58.dp, bottom = 154.dp),
            factory = { androidContext ->
                createSecureWebView(
                    context = androidContext,
                    onReady = { webView = it },
                    onUrlChanged = viewModel::onUrlChanged,
                    onExtractionStarted = viewModel::beginAnalysis,
                    onExtracted = viewModel::analyzeJavascriptResult,
                    onExtractionTimeout = viewModel::reportExtractionTimeout,
                    onPageError = viewModel::reportPageError,
                )
            },
        )

        Surface(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹ 返回") }
                Text("从教务系统导入", style = MaterialTheme.typography.titleMedium)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "密码只提交给学校统一认证，本应用不读取或保存。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "切换学期后，请等网页课表刷新完成再识别。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        webView?.extractTimetable(
                            onStarted = viewModel::beginAnalysis,
                            onExtracted = viewModel::analyzeJavascriptResult,
                            onTimeout = viewModel::reportExtractionTimeout,
                        )
                    },
                    enabled = !state.isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("识别当前页面")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            webView = null
        }
    }

    state.pendingSchedule?.let { schedule ->
        val courseCount = schedule.lessons.map { it.courseName to it.teacher }.distinct().size
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text("确认导入课表") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(schedule.semester.name, style = MaterialTheme.typography.titleMedium)
                    Text("识别到 $courseCount 门课程、${schedule.lessons.size} 条上课安排。")
                    Text("有效周次最高到第 ${schedule.semester.totalWeeks} 周。")
                    schedule.warnings.forEach { warning ->
                        Text("• $warning", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    schedule.lessons.take(4).forEach { lesson ->
                        val room = lesson.room.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
                        Text(
                            "${lesson.courseName} · 周${lesson.weekday} " +
                                "第${lesson.startPeriod}-${lesson.endPeriod}节$room · ${WeekPattern.format(lesson.weeks)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPreview, enabled = !state.isSaving) { Text("继续核对") }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImport, enabled = !state.isSaving) {
                    Text(if (state.isSaving) "保存中…" else "导入并替换本学期")
                }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun createSecureWebView(
    context: android.content.Context,
    onReady: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onExtractionStarted: () -> Unit,
    onExtracted: (String?) -> Unit,
    onExtractionTimeout: () -> Unit,
    onPageError: (String) -> Unit,
): WebView = WebView(context).apply {
    val currentWebView = this
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    WebView.setWebContentsDebuggingEnabled(BuildConfig.WEBVIEW_DEBUG)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        databaseEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        saveFormData = false
        savePassword = false
        safeBrowsingEnabled = true
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(currentWebView, false)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?,
        ): Boolean = false
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (AllowedHostPolicy.isAllowed(target)) return false
            Toast.makeText(context, "为保护登录信息，已阻止离开学校教务域名。", Toast.LENGTH_LONG).show()
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            onUrlChanged(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            onUrlChanged(url)
            if (AllowedHostPolicy.isTeachingSystemPage(url)) {
                view.postDelayed({
                    runCatching {
                        view.extractTimetable(
                            onStarted = onExtractionStarted,
                            onExtracted = onExtracted,
                            onTimeout = onExtractionTimeout,
                        )
                    }
                }, 1_200L)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) onPageError(error.description.toString())
        }
    }
    loadUrl(BistuPageExtractor.START_URL)
    onReady(this)
}

private fun WebView.extractTimetable(
    onStarted: () -> Unit,
    onExtracted: (String?) -> Unit,
    onTimeout: () -> Unit,
) {
    onStarted()
    evaluateJavascript(BistuPageExtractor.script) {
        pollTimetableResult(
            remainingAttempts = 60,
            onExtracted = onExtracted,
            onTimeout = onTimeout,
        )
    }
}

private fun WebView.pollTimetableResult(
    remainingAttempts: Int,
    onExtracted: (String?) -> Unit,
    onTimeout: () -> Unit,
) {
    if (remainingAttempts <= 0) {
        onTimeout()
        return
    }
    evaluateJavascript(BistuPageExtractor.resultScript) { raw ->
        if (raw.isNullOrBlank() || raw == "null") {
            postDelayed(
                {
                    pollTimetableResult(
                        remainingAttempts = remainingAttempts - 1,
                        onExtracted = onExtracted,
                        onTimeout = onTimeout,
                    )
                },
                250L,
            )
        } else {
            onExtracted(raw)
        }
    }
}
