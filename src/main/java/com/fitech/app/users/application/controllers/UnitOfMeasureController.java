package com.fitech.app.users.application.controllers;

import com.fitech.app.commons.application.controllers.BaseController;
import com.fitech.app.users.domain.model.UnitOfMeasureDto;
import com.fitech.app.users.application.dto.ResultPage;
import com.fitech.app.users.domain.services.UnitOfMeasureService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/app/units-of-measure")
public class UnitOfMeasureController extends BaseController {

    private final UnitOfMeasureService unitOfMeasureService;

    @Autowired
    public UnitOfMeasureController(UnitOfMeasureService unitOfMeasureService) {
        this.unitOfMeasureService = unitOfMeasureService;
    }

    @PostMapping
    public ResponseEntity<UnitOfMeasureDto> create(@Valid @RequestBody UnitOfMeasureDto unitOfMeasureDto) {
        UnitOfMeasureDto createdUnit = unitOfMeasureService.create(unitOfMeasureDto);
        return new ResponseEntity<>(createdUnit, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitOfMeasureDto> update(@PathVariable Integer id, @Valid @RequestBody UnitOfMeasureDto unitOfMeasureDto) {
        UnitOfMeasureDto updatedUnit = unitOfMeasureService.update(id, unitOfMeasureDto);
        return ResponseEntity.ok(updatedUnit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        unitOfMeasureService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UnitOfMeasureDto> findById(@PathVariable Integer id) {
        UnitOfMeasureDto unitOfMeasure = unitOfMeasureService.findById(id);
        return ResponseEntity.ok(unitOfMeasure);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable paging = PageRequest.of(page - 1, size);
        ResultPage<UnitOfMeasureDto> resultPageWrapper = unitOfMeasureService.findAll(paging);
        Map<String, Object> response = prepareResponse(resultPageWrapper);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    protected String getResource() {
        return "units-of-measure";
    }
} 