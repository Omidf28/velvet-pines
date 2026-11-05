package com.subtitleplayer.prototype

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.net.URI

internal class SmbCredentials(private val username: String, private val password: CharArray) {
    fun authenticationContext(): AuthenticationContext =
        AuthenticationContext(
            username.substringAfter('\\'),
            password,
            username.substringBefore('\\', missingDelimiterValue = "")
        )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SmbVideoDataSource(private val credentials: SmbCredentials?) : BaseDataSource(true) {
    private var openedUri: Uri? = null
    private var client: SMBClient? = null
    private var share: DiskShare? = null
    private var file: File? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            val location = parseSmbFileLocation(URI(dataSpec.uri.toString()))
                ?: throw IOException("Invalid SMB file location")
            val smb = newSmbClient()
            client = smb
            val disk = smb.openDiskShare(location, credentials)
            share = disk
            val remote = disk.openFile(
                location.path,
                setOf(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            file = remote
            val size = remote.getFileInformation(FileStandardInformation::class.java).endOfFile
            position = dataSpec.position
            if (position > size) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - position
            else minOf(dataSpec.length, size - position)
            openedUri = dataSpec.uri
            transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) {
            close()
            if (error is DataSourceException) throw error
            throw IOException("Unable to open SMB video", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val remote = file ?: throw IOException("SMB video is not open")
        val bytesRead = try {
            remote.read(buffer, position, offset, minOf(remaining, length.toLong()).toInt())
        } catch (error: RuntimeException) {
            throw IOException("Unable to read SMB video", error)
        }
        if (bytesRead <= 0) return C.RESULT_END_OF_INPUT
        position += bytesRead
        remaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        runCatching { file?.close() }
        runCatching { share?.close() }
        runCatching { client?.close() }
        file = null
        share = null
        client = null
        if (openedUri != null) {
            openedUri = null
            transferEnded()
        }
    }
}
