package com.jonny.keyscope.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The official YouTube IFrame embed in a WebView.
 *
 * Nothing is downloaded, stripped or intercepted — this is the embed YouTube publishes for the
 * purpose, ads and all. The audio comes out of the phone speaker on purpose: the microphone is
 * what reads it, which is the only route that stays inside their terms.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(videoId: String, modifier: Modifier = Modifier) {
    // Remembers what is already loaded so recomposition does not restart playback mid-reading.
    val loaded = remember { arrayOfNulls<String>(1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Without this the embed will not start without a tap of its own.
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                setBackgroundColor(Color.BLACK)
            }
        },
        update = { web ->
            if (loaded[0] != videoId) {
                loaded[0] = videoId
                web.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    pageFor(videoId),
                    "text/html",
                    "utf-8",
                    null
                )
            }
        },
        onRelease = { web ->
            web.loadUrl("about:blank")
            web.destroy()
        }
    )
}

private fun pageFor(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<style>
  html, body { margin:0; padding:0; background:#000; height:100%; overflow:hidden; }
  #player { width:100%; height:100%; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: { autoplay: 1, playsinline: 1, rel: 0, modestbranding: 1 },
      events: { onReady: function (event) { event.target.playVideo(); } }
    });
  }
</script>
</body>
</html>
""".trimIndent()
