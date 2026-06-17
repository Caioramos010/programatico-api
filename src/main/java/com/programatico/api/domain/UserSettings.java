package com.programatico.api.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "disable_update_notifications", nullable = false)
    @Builder.Default
    private Boolean disableUpdateNotifications = false;

    @Column(name = "disable_daystreak_notifications", nullable = false)
    @Builder.Default
    private Boolean disableDaystreakNotifications = false;

    @Column(name = "disable_mission_notifications", nullable = false)
    @Builder.Default
    private Boolean disableMissionNotifications = false;

    @Column(name = "disable_subscription_notifications", nullable = false)
    @Builder.Default
    private Boolean disableSubscriptionNotifications = false;

    @Column(name = "disable_email_notifications", nullable = false)
    @Builder.Default
    private Boolean disableEmailNotifications = false;

    @Column(name = "disable_all_notifications", nullable = false)
    @Builder.Default
    private Boolean disableAllNotifications = false;
}
