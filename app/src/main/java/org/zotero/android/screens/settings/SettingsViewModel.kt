package org.zotero.android.screens.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import org.zotero.android.architecture.BaseViewModel2
import org.zotero.android.architecture.Defaults
import org.zotero.android.architecture.ViewEffect
import org.zotero.android.architecture.ViewState
import javax.inject.Inject

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val defaults: Defaults,
) : BaseViewModel2<SettingsViewState, SettingsViewEffect>(SettingsViewState()) {

    fun init() = initOnce {
        updateState {
            copy(
                openPdfWithExternalApp = defaults.isOpenPdfWithExternalApp(),
                linkedAttachmentBaseDir = defaults.getLinkedAttachmentBaseDirectory(),
            )
        }
    }

    fun onOpenPdfWithExternalAppChanged(enabled: Boolean) {
        defaults.setOpenPdfWithExternalApp(enabled)
        updateState {
            copy(openPdfWithExternalApp = enabled)
        }
    }

    fun onShowBaseDirDialog(show: Boolean) {
        updateState {
            copy(isBaseDirDialogVisible = show)
        }
    }

    fun onSaveBaseDirectory(path: String?, uri: String? = null) {
        val trimmed = path?.trim()?.ifBlank { null }
        defaults.setLinkedAttachmentBaseDirectory(trimmed)
        if (uri != null) {
            defaults.setLinkedAttachmentBaseDirectoryUri(uri)
        } else if (trimmed == null) {
            defaults.setLinkedAttachmentBaseDirectoryUri(null)
        }
        updateState {
            copy(
                linkedAttachmentBaseDir = trimmed,
                isBaseDirDialogVisible = false
            )
        }
    }

    fun onDone() {
        triggerEffect(SettingsViewEffect.OnBack)
    }

    fun openPrivacyPolicy() {
        val uri = "https://www.zotero.org/support/privacy?app=1"
        triggerEffect(SettingsViewEffect.OpenWebpage(uri))
    }

    fun openSupportAndFeedback() {
        val uri = "https://forums.zotero.org/"
        triggerEffect(SettingsViewEffect.OpenWebpage(uri))
    }
}

internal data class SettingsViewState(
    val openPdfWithExternalApp: Boolean = false,
    val linkedAttachmentBaseDir: String? = null,
    val isBaseDirDialogVisible: Boolean = false,
) : ViewState

internal sealed class SettingsViewEffect : ViewEffect {
    object OnBack : SettingsViewEffect()
    data class OpenWebpage(val url: String) : SettingsViewEffect()
}