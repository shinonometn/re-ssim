package com.shinonometn.re.ssim.caterpillar.application.service

import com.shinonometn.re.ssim.caterpillar.application.entity.CaterpillarSetting
import com.shinonometn.re.ssim.caterpillar.application.repository.CaterpillarSettingRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
class CaterpillarProfileService(private val caterpillarSettingRepository: CaterpillarSettingRepository) {
    fun findProfile(username: String, name: String): Optional<CaterpillarSetting>
            = caterpillarSettingRepository.findByOwnerAndName(username, name)

    fun save(caterpillarSetting: CaterpillarSetting): CaterpillarSetting =
            caterpillarSettingRepository.save(caterpillarSetting)

    fun findAll(pageable: Pageable): Page<CaterpillarSetting>
            = caterpillarSettingRepository.findAll(pageable)

    fun findAllByUser(username: String, pageable: Pageable): Page<CaterpillarSetting>
            = caterpillarSettingRepository.findAllByOwner(username, pageable)

    fun findById(id: Int): Optional<CaterpillarSetting>
            = caterpillarSettingRepository.findById(id)
}
