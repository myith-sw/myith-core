package com.myith.core.application.port;

import com.myith.core.domain.diagnosis.UserDiagnosis;

import java.util.List;

public interface DiagnosisRepository {
    List<UserDiagnosis> saveAll(List<UserDiagnosis> diagnoses);
}
