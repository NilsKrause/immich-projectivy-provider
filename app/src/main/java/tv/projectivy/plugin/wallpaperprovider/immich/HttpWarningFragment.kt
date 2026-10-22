package tv.projectivy.plugin.wallpaperprovider.immich

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction

/**
 * Confirmation shown when the user configures an unencrypted (`http://`) Immich server URL.
 * The API key travels in the wallpaper URLs' query string, so over HTTP it would be readable
 * by anyone on the network.
 */
class HttpWarningFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            getString(R.string.http_warning_title),
            getString(R.string.http_warning_message),
            null,
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        PreferencesManager.init(requireContext())

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CONTINUE)
                .title(getString(R.string.http_warning_continue))
                .build()
        )
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title(getString(R.string.http_warning_cancel))
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val url = arguments?.getString(ARG_URL) ?: ""
        when (action.id) {
            ACTION_CONTINUE -> PreferencesManager.serverUrl = url
            ACTION_CANCEL -> PreferencesManager.serverUrl = ""
        }
        requireFragmentManager().popBackStack()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ACTION_CONTINUE = 1L
        private const val ACTION_CANCEL = 2L

        fun show(fragmentManager: FragmentManager, url: String) {
            val fragment = HttpWarningFragment()
            fragment.arguments = Bundle().apply { putString(ARG_URL, url) }
            GuidedStepSupportFragment.add(fragmentManager, fragment)
        }
    }
}
