package com.example.demo.config;

import com.example.demo.model.Material;
import com.example.demo.model.Maquinaria;
import com.example.demo.repository.MaterialRepository;
import com.example.demo.repository.MaquinariaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Carga optativa de catalogos ficticios, solamente si estan vacios. */
@Configuration
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoResourcesConfig {
    @Bean
    ApplicationRunner demoResources(MaterialRepository materials, MaquinariaRepository machinery) {
        return args -> {
            if (materials.count() == 0) {
                materials.saveAll(List.of(new Material("DEMO - Asfalto", "toneladas"),
                        new Material("DEMO - Cemento", "bolsas"),
                        new Material("DEMO - Arena", "m3")));
            }
            if (machinery.count() == 0) {
                machinery.saveAll(List.of(new Maquinaria("DEMO - Retroexcavadora"),
                        new Maquinaria("DEMO - Compactadora")));
            }
        };
    }
}
