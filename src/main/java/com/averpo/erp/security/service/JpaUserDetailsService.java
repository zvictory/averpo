package com.averpo.erp.security.service;

import com.averpo.erp.security.repo.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security'ни app_user жадвалига улайди - form login шу ердан
 * фойдаланувчини топади.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    /** Фойдаланувчилар репозиторийси. */
    private final AppUserRepository repository;

    /**
     * {@inheritDoc}
     *
     * <p>disabled - BR-USR-010 (нофаол user DisabledException билан
     * қайтади); accountLocked - BR-USR-009 (locked_until келажакда
     * бўлса LockedException, парол текширилмасдан ҳам - Spring Security
     * pre-auth текшируви). Иккала қоида ҳам шу битта жойда ижро этилади.
     *
     * <p>Authority'лар (DEC-092) роль номидан эмас,
     * {@link com.averpo.erp.security.domain.RolePermissions}
     * матрицасидан келади: ROLE_&lt;роль&gt; + соҳа VIEW/EDIT'лари +
     * имкониятлар - SecurityConfig соҳа қоидалари айнан шуларни
     * текширади.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Фойдаланувчи топилмади: " + username));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(com.averpo.erp.security.domain.RolePermissions
                        .authorities(user.getRole()))
                .disabled(!user.isActive())
                .accountLocked(user.lockedAt(java.time.Instant.now()))
                .build();
    }
}
