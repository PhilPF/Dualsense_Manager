package com.philpf.dualsensemanager.device

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

    /** Saves [rgb] as the colour of the controller's player, or of the controller when it has none. */
    fun setColor(controllerId: String, rgb: Int)

    /** Back to the controller's automatic colour (for a controller without a player). */
    fun useAutomaticColor(controllerId: String)

    fun assignPlayer(controllerId: String, playerId: String?)

    /** Creates a player with a default name and the controller's colour, gives it the controller, returns its id. */
    fun newPlayer(controllerId: String): String?

    fun renamePlayer(playerId: String, name: String)

    fun deletePlayer(playerId: String)

    /** Rumbles the controller and blinks its lights, to tell which one it is. */
    fun identify(controllerId: String)

    /** Forgets a controller that is not connected. */
    fun forget(controllerId: String)
}
