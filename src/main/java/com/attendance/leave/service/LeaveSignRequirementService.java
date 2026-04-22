package com.attendance.leave.service;

import com.attendance.admin.mapper.LeaveSignRequirementMapper;
import com.attendance.admin.model.LeaveSignRequirement;
import com.attendance.leave.enums.RoleCode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin
@Service
@RequiredArgsConstructor
public class LeaveSignRequirementService {

    private static final Set<String> MANDATORY_SIGNATURE_ROLES = Set.of(
            RoleCode.ORG_PRINCIPAL,
            RoleCode.HR_SECTION_CHIEF,
            RoleCode.UNIT_DEPUTY_LEADER,
            RoleCode.UNIT_PRINCIPAL_LEADER,
            RoleCode.DEPUTY_STATIONMASTER,
            RoleCode.STATIONMASTER,
            RoleCode.PARTY_SECRETARY
    );

    private final LeaveSignRequirementMapper leaveSignRequirementMapper;

    public boolean isSignatureRequired(String roleCode, Long leaveTypeId) {
        if (MANDATORY_SIGNATURE_ROLES.contains(roleCode)) {
            return true;
        }
        LeaveSignRequirement requirement = leaveSignRequirementMapper.findActive(roleCode, leaveTypeId);
        if (requirement == null) {
            return true;
        }
        return requirement.getSignRequired() != null && requirement.getSignRequired() == 1;
    }
}
