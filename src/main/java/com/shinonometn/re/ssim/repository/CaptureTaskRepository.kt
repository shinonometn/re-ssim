package com.shinonometn.re.ssim.repository

import com.shinonometn.re.ssim.models.CaptureTask
import com.shinonometn.re.ssim.models.CaptureTaskDTO
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.CrudRepository

interface CaptureTaskRepository : CrudRepository<CaptureTask, String> {

    @Query("{}")
    fun findAllProjected(): List<CaptureTaskDTO>

    @Query(value = "{'_id' : ?0}")
    fun findProjectedById(id: String): CaptureTaskDTO

}
