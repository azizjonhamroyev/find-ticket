package uz.aziz.lookingforticket.db

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BrandInitializer(
    private val brandRepository: BrandRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @PostConstruct
    fun initializeBrands() {
        val existingBrands = brandRepository.findAll()
        if (existingBrands.isNotEmpty()) {
            logger.info("Brands already initialized, skipping...")
            return
        }
        
        val brands = listOf(
            BrandEntity(name = "скорый", displayName = "скорый"),
            BrandEntity(name = "Sharq", displayName = "Sharq"),
            BrandEntity(name = "Afrosiyob", displayName = "Afrosiyob"),
            BrandEntity(name = "Пассажирский", displayName = "Пассажирский")
        )
        
        brandRepository.saveAll(brands)
        logger.info("Initialized ${brands.size} brands")
    }
}




