package rw.rra.roomiq.identity.domain.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.rra.roomiq.identity.domain.repository.AppUserRepository;

@Service
public class IdentityUserDetailsService implements UserDetailsService {
    private final AppUserRepository userRepository;

    public IdentityUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(username.trim())
                .map(IdentityUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User credentials were not accepted"));
    }
}
