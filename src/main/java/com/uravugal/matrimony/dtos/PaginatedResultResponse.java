package com.uravugal.matrimony.dtos;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PaginatedResultResponse extends ResultResponse {
    private PaginationData paginationData;
}
