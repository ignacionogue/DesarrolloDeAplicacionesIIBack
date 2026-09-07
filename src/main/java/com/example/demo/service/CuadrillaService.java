package com.example.demo.service;

import com.example.demo.dto.request.CuadrillaRequest;
import com.example.demo.dto.response.CuadrillaResponse;
import com.example.demo.exception.BusinessRuleException;
import com.example.demo.model.Cuadrilla;
import com.example.demo.repository.CuadrillaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CuadrillaService {

    private final CuadrillaRepository repository;

    public CuadrillaService(CuadrillaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CuadrillaResponse> listar() {
        return repository.findAll().stream()
                .map(c -> new CuadrillaResponse(c.getId(), c.getNombre()))
                .toList();
    }

    @Transactional
    public CuadrillaResponse crear(CuadrillaRequest request) {
        repository.findByNombreIgnoreCase(request.getNombre()).ifPresent(c -> {
            throw new BusinessRuleException("Ya existe una cuadrilla con ese nombre");
        });
        Cuadrilla guardada = repository.save(new Cuadrilla(request.getNombre()));
        return new CuadrillaResponse(guardada.getId(), guardada.getNombre());
    }
}
