package com.example.demo.service;

import com.example.demo.dto.response.CuadrillaResponse;
import com.example.demo.dto.response.MaquinariaResponse;
import com.example.demo.dto.response.MaterialResponse;
import com.example.demo.dto.response.ResourcesSummaryResponse;
import com.example.demo.repository.CuadrillaRepository;
import com.example.demo.repository.MaquinariaRepository;
import com.example.demo.repository.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourcesService {

    private final CuadrillaRepository cuadrillaRepository;
    private final MaterialRepository materialRepository;
    private final MaquinariaRepository maquinariaRepository;

    public ResourcesService(CuadrillaRepository cuadrillaRepository, MaterialRepository materialRepository,
                             MaquinariaRepository maquinariaRepository) {
        this.cuadrillaRepository = cuadrillaRepository;
        this.materialRepository = materialRepository;
        this.maquinariaRepository = maquinariaRepository;
    }

    @Transactional(readOnly = true)
    public ResourcesSummaryResponse obtenerResumen() {
        var crews = cuadrillaRepository.findAll().stream()
                .map(c -> new CuadrillaResponse(c.getId(), c.getNombre()))
                .toList();
        var materials = materialRepository.findAll().stream()
                .map(m -> new MaterialResponse(m.getId(), m.getNombre(), m.getUnidad()))
                .toList();
        var machinery = maquinariaRepository.findAll().stream()
                .map(m -> new MaquinariaResponse(m.getId(), m.getNombre()))
                .toList();
        return new ResourcesSummaryResponse(crews, materials, machinery);
    }
}
