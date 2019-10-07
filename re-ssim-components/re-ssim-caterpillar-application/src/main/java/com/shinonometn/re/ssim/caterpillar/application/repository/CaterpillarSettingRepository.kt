package com.shinonometn.re.ssim.caterpillar.application.repository

import com.shinonometn.re.ssim.caterpillar.application.entity.CaterpillarSetting
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CaterpillarSettingRepository: MongoRepository<CaterpillarSetting,String> {

    fun findByOwnerAndName(username: String, name: String): Optional<CaterpillarSetting>

    fun findAllByOwner(username: String, pageable: Pageable): Page<CaterpillarSetting>
}