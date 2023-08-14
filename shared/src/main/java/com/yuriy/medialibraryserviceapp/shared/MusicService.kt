package com.yuriy.medialibraryserviceapp.shared

import android.app.PendingIntent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaLibraryService() {

    private lateinit var mSession: MediaLibrarySession

    private val mPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes, true)
        }
        player
    }

    override fun onCreate() {
        super.onCreate()

        mSession = with(
            MediaLibrarySession.Builder(
                this, mPlayer, ServiceCallback()
            )
        ) {
            setId(packageName)
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        applicationContext,
                        0,
                        sessionIntent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mSession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.release()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mSession
    }

    companion object {

        private const val TAG = "MLSA"
        private const val MEDIA_ID_ROOT = "MEDIA_ID_ROOT"
        private const val MEDIA_ID_RADIOS = "MEDIA_ID_RADIOS"
    }

    private inner class ServiceCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMediaItem = buildRootMediaItem()
            val libraryParams = LibraryParams.Builder().build()
            Log.d(TAG, "Get root")
            return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, libraryParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            Log.d(TAG, "Get children for $parentId")
            val children = ArrayList<MediaItem>()
            when (parentId) {
                MEDIA_ID_ROOT -> {
                    children.add(buildBrowseMenuItem(MEDIA_ID_RADIOS, "Radios"))
                }

                MEDIA_ID_RADIOS -> {
                    for (i in 0..10) {
                        children.add(buildPlayable(i.toString()))
                    }

                    Handler(Looper.myLooper()!!).postDelayed({
                        Log.d(TAG, "Trigger notifyChildrenChanged for $parentId")
                        session.notifyChildrenChanged(browser, parentId, children.size, params)
                    }, 10_000)
                }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        private fun buildRootMediaItem(): MediaItem {
            return MediaItem.Builder()
                .setMediaId(MEDIA_ID_ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }

        private fun buildBrowseMenuItem(mediaId: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }

        private fun buildPlayable(mediaId: String): MediaItem {
            val bundle = Bundle()
            val uri = Uri.parse("http://some.radio.station")
            val imageUri = Uri.parse("content://some.radio.station.image")
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(uri)
                .setMimeType(MimeTypes.AUDIO_MPEG)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setTitle("Title $mediaId")
                        .setSubtitle("Sub Title $mediaId")
                        .setDescription("Genre")
                        .setArtworkUri(imageUri)
                        .setExtras(bundle)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
    }
}
