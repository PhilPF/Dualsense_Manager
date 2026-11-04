package com.philpf.dualsensemanager.device

import android.content.IntentSender
import com.philpf.dualsensemanager.core.ControllerProfile
import com.philpf.dualsensemanager.core.LiveController
import com.philpf.dualsensemanager.core.Player
import kotlinx.coroutines.flow.StateFlow

/** What the screen shows and can do. [ControllerManager] is the real one. */
interface ControllerSource {
    val profiles: StateFlow<Map<String, ControllerProfile>>
    val players: StateFlow<Map<String, Player>>
    val live: StateFlow<List<LiveController>>

    /** Colours being tried in the colour picker, by controller id. Not saved yet. */
    val previews: StateFlow<Map<String, Int>>

    /** Shows [rgb] on the controller without saving it, while a slider is dragged. */
    fun preview(controllerId: String, rgb: Int)

    /** Drops a preview, going back to the saved colour. */
    fun endPreview(controllerId: String)

    /** Saves [rgb] as the colour of the controller's player, or of the controller when it has none. */
    fun setColor(controllerId: String, rgb: Int)

    /** Back to the controller's automatic colour (for a controller without a player). */
    fun useAutomaticColor(controllerId: String)

    fun assignPlayer(controllerId: String, playerId: String?)

    /** Creates a player and gives it the controller; returns the player's id. */
    fun newPlayer(controllerId: String, name: String, color: Int): String?

    fun updatePlayer(playerId: String, name: String, color: Int)

    fun deletePlayer(playerId: String)

    /** Rumbles the controller and blinks its lights, to tell which one it is. */
    fun identify(controllerId: String)

    /** Whether the controller can be disconnected: it is connected on Bluetooth. */
    fun canDisconnect(controllerId: String): Boolean

    /**
     * Disconnects the controller, which turns it off. From Android 17 the app does it; the first
     * time for each controller, the system asks the user to let the app manage it: [askUser]
     * gets that prompt to show. Before, [onResult] gets where in the system settings to do it.
     */
    fun disconnect(controllerId: String, askUser: (IntentSender) -> Unit, onResult: (DisconnectResult) -> Unit)

    /** Forgets a controller that is not connected. */
    fun forget(controllerId: String)

    /** Puts controllers in this order, renumbering them (swapping two swaps their numbers). */
    fun reorder(order: List<String>)
}
