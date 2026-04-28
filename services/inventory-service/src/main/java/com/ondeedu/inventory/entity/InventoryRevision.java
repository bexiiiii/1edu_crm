package com.ondeedu.inventory.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_revisions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRevision extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "revision_date", nullable = false)
    private LocalDate revisionDate;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "total_items", nullable = false)
    @Builder.Default
    private int totalItems = 0;

    @Column(name = "surplus_items", nullable = false)
    @Builder.Default
    private int surplusItems = 0;

    @Column(name = "shortage_items", nullable = false)
    @Builder.Default
    private int shortageItems = 0;

    @Column(name = "ok_items", nullable = false)
    @Builder.Default
    private int okItems = 0;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryRevisionItem> items = new ArrayList<>();
}
