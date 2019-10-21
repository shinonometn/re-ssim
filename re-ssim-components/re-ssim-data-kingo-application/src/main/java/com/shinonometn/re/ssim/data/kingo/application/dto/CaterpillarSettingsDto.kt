package com.shinonometn.re.ssim.data.kingo.application.dto

import com.shinonometn.re.ssim.data.kingo.application.agent.CaterpillarProfileAgent
import com.shinonometn.re.ssim.data.kingo.application.entity.CaterpillarSetting

data class CaterpillarSettingsDto(private val map: MutableMap<String, Any?> = HashMap()) {
    var caterpillarSetting: CaterpillarSetting by map
    var agentProfileInfo: CaterpillarProfileAgent by map
}