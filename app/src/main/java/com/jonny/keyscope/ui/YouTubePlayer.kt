package com.jonny.keyscope.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** What the embedded player is doing, reported back from its own JavaScript. */
sealed interface PlayerStatus {
    data object Loading : PlayerStatus
    data object Buffering : PlayerStatus
    data object Playing : PlayerStatus
    data object Paused : PlayerStatus
    data object Ended : PlayerStatus

    /** The player reported a real failure; [message] is already human-readable. */
    data class Failed(val code: Int, val message: String) : PlayerStatus
}

/**
 * The official YouTube IFrame embed in a WebView.
 *
 * Nothing is downloaded, stripped or intercepted — this is the embed YouTube publishes for the
 * purpose, ads and all. The audio comes out of the phone speaker on purpose: the microphone is
 * what reads it, which is the only route that stays inside their terms.
 *
 * Two things the obvious version of this gets wrong. Autoplay with sound is blocked by policy, so
 * a player left to start on its own comes up muted — which means silence for you and nothing at
 * all for the mic. And a great many official uploads simply forbid embedding, which shows up as a
 * black rectangle unless the error is surfaced. Both are handled here.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    videoId: String,
    playRequest: Int,
    onStatus: (PlayerStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusCallback by rememberUpdatedState(onStatus)
    val loaded = remember { arrayOfNulls<String>(1) }
    val lastPlayRequest = remember { intArrayOf(-1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Lets the embed start on its own. It still comes up muted, which is what the
                // unMute call in the page is for.
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                setBackgroundColor(Color.BLACK)

                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onState(code: Int) {
                            main.post {
                                statusCallback(
                                    when (code) {
                                        1 -> PlayerStatus.Playing
                                        2 -> PlayerStatus.Paused
                                        3 -> PlayerStatus.Buffering
                                        0 -> PlayerStatus.Ended
                                        else -> PlayerStatus.Loading
                                    }
                                )
                            }
                        }

                        @JavascriptInterface
                        fun onError(code: Int) {
                            main.post { statusCallback(PlayerStatus.Failed(code, describe(code))) }
                        }
                    },
                    "KeyBro"
                )
            }
        },
        update = { web ->
            if (loaded[0] != videoId) {
                loaded[0] = videoId
                lastPlayRequest[0] = playRequest
                statusCallback(PlayerStatus.Loading)
                web.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    pageFor(videoId),
                    "text/html",
                    "utf-8",
                    null
                )
            } else if (lastPlayRequest[0] != playRequest) {
                lastPlayRequest[0] = playRequest
                // A tap is a user gesture, which is the one thing that unambiguously permits
                // sound. Worth having as a fallback for whatever policy applies that day.
                web.evaluateJavascript("keybroPlay();", null)
            }
        },
        onRelease = { web ->
            web.loadUrl("about:blank")
            web.destroy()
        }
    )
}

private fun describe(code: Int): String = when (code) {
    2 -> "That video id was rejected."
    5 -> "The player could not handle this video."
    100 -> "That video is gone or private."
    101, 150 -> "This upload does not allow embedding. Try another upload of the same track."
    else -> "The player failed with error $code."
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

  function keybroSound() {
    if (!player) return;
    // The whole point is that it comes out of the speaker, so nothing here is left muted.
    try { player.unMute(); player.setVolume(100); } catch (e) {}
  }

  function keybroPlay() {
    if (!player) return;
    keybroSound();
    try { player.playVideo(); } catch (e) {}
  }

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: { autoplay: 1, playsinline: 1, rel: 0, modestbranding: 1 },
      events: {
        onReady: function (event) {
          keybroSound();
          event.target.playVideo();
          // Policy sometimes re-mutes on the transition into playing, so do it again once it
          // has actually started.
          setTimeout(keybroSound, 400);
          setTimeout(keybroSound, 1500);
        },
        onStateChange: function (event) {
          if (event.data === 1) keybroSound();
          if (window.KeyBro) KeyBro.onState(event.data);
        },
        onError: function (event) {
          if (window.KeyBro) KeyBro.onError(event.data);
        }
      }
    });
  }
</script>
</body>
</html>
""".trimIndent()
