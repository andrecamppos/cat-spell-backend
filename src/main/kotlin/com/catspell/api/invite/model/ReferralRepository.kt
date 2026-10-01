package com.catspell.api.invite.model

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReferralRepository : JpaRepository<Referral, UUID>
