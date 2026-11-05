package com.subtitleplayer.prototype

import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun newSmbClient(): SMBClient = SMBClient(
    SmbConfig.builder()
        .withTimeout(10, TimeUnit.SECONDS)
        .withSoTimeout(10, TimeUnit.SECONDS)
        .build()
)

internal fun SMBClient.openDiskShare(location: SmbFileLocation, credentials: SmbCredentials?): DiskShare =
    connect(location.host, location.port)
        .authenticate(credentials?.authenticationContext() ?: AuthenticationContext.guest())
        .connectShare(location.share) as? DiskShare ?: throw IOException("SMB share is not a disk share")

internal class SmbBrowseRequest(val location: SmbFileLocation, val credentials: SmbCredentials?)
internal data class SmbBrowserEntry(val name: String, val directory: Boolean)

internal fun readSmbDirectory(location: SmbFileLocation, credentials: SmbCredentials?): List<SmbBrowserEntry> {
    try {
        return newSmbClient().use { client ->
            client.openDiskShare(location, credentials).use { share ->
                share.list(location.path).mapNotNull { info ->
                    val name = info.fileName
                    if (!isSmbEntryName(name)) return@mapNotNull null
                    val directory = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
                    if (directory || isVideo(name, null)) SmbBrowserEntry(name, directory) else null
                }.sortedWith(
                    Comparator { a, b ->
                        if (a.directory != b.directory) {
                            if (a.directory) -1 else 1
                        } else compareNaturalNames(a.name, b.name)
                    }
                )
            }
        }
    } catch (error: RuntimeException) {
        throw IOException("Unable to browse SMB folder", error)
    }
}
