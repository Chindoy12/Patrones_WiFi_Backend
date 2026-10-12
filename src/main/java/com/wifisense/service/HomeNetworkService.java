package com.wifisense.service;

import com.wifisense.dto.HomeNetworkRequest;
import com.wifisense.dto.HomeNetworkResponse;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * End users manage only their own networks. A network of someone else answers exactly like one that does not
 * exist, so ids cannot be used to discover other accounts.
 */
@Service
public class HomeNetworkService {

    public static final int MAX_PER_USER = 3;

    private final HomeNetworkRepository networks;
    private final UserRepository users;

    public HomeNetworkService(HomeNetworkRepository networks, UserRepository users) {
        this.networks = networks;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<HomeNetworkResponse> list(String username) {
        return networks.findByOwner_UsernameOrderByCreatedAtAsc(username).stream().map(HomeNetworkResponse::from).toList();
    }

    @Transactional
    public HomeNetworkResponse create(String username, HomeNetworkRequest request) {
        if (networks.countByOwner_Username(username) >= MAX_PER_USER) {
            throw new IllegalStateException("Puedes registrar hasta " + MAX_PER_USER + " redes. Elimina una para agregar otra.");
        }
        String name = request.name().trim();
        if (networks.existsByOwner_UsernameAndNameIgnoreCase(username, name)) {
            throw new DuplicateResourceException("Ya tienes una red con ese nombre.");
        }
        User owner = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("el usuario", username));
        return HomeNetworkResponse.from(networks.save(new HomeNetwork(owner, name, clean(request.provider()), request.contractedMbps())));
    }

    @Transactional
    public HomeNetworkResponse update(String username, Long id, HomeNetworkRequest request) {
        HomeNetwork network = owned(username, id);
        String name = request.name().trim();
        if (!network.getName().equalsIgnoreCase(name) && networks.existsByOwner_UsernameAndNameIgnoreCase(username, name)) {
            throw new DuplicateResourceException("Ya tienes una red con ese nombre.");
        }
        network.update(name, clean(request.provider()), request.contractedMbps());
        return HomeNetworkResponse.from(network);
    }

    @Transactional
    public void delete(String username, Long id) {
        networks.delete(owned(username, id));
    }

    HomeNetwork owned(String username, Long id) {
        return networks.findByIdAndOwner_Username(id, username).orElseThrow(() -> new ResourceNotFoundException("la red", id));
    }

    private static String clean(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
