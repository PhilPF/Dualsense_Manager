package com.philpf.dualsensemanager.device

import com.philpf.dualsensemanager.core.Headphones
import com.philpf.dualsensemanager.core.NoiseControl
import com.philpf.dualsensemanager.core.NoiseMode
import com.philpf.dualsensemanager.core.SonyEqualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Debug builds' sample headphones, for screenshots: a WH-XB910N, connected. */
class DemoHeadphones : HeadphonesSource {
    private val _headphones = MutableStateFlow(
        listOf(
            Headphones(
                address = ADDRESS,
                name = "WH-XB910N",
                connected = true,
                ready = true,
                battery = 70,
                noise = NoiseControl(NoiseMode.NOISE_CANCELLING, ambientLevel = 12, voiceFocus = true),
                equalizer = SonyEqualizer.BASS_BOOST,
            ),
        ),
    )
    override val headphones: StateFlow<List<Headphones>> = _headphones.asStateFlow()

    override fun refresh() = Unit

    override fun setNoise(address: String, control: NoiseControl) = change(address) { it.copy(noise = control) }

    override fun setEqualizer(address: String, preset: SonyEqualizer) = change(address) { it.copy(equalizer = preset) }

    override fun powerOff(address: String) = change(address) { Headphones(it.address, it.name, connected = false) }

    private fun change(address: String, transform: (Headphones) -> Headphones) {
        _headphones.update { list -> list.map { if (it.address == address) transform(it) else it } }
    }

    companion object {
        const val ADDRESS = "demo-headphones"
    }
}
