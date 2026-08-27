package com.flamingo.tictactoe.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    private List<Route> routes() {
        return Flux.from(routeLocator.getRoutes()).collectList().block();
    }

    @Test
    void definesARouteForEachBackendPlusTheUi() {
        assertThat(routes()).extracting(Route::getId)
                .containsExactlyInAnyOrder("sessions", "games", "ui");
    }

    @Test
    void apiRoutesAreMatchedBeforeTheUiCatchAll() {
        List<Route> ordered = routes().stream()
                .sorted(java.util.Comparator.comparingInt(Route::getOrder))
                .toList();

        assertThat(ordered).extracting(Route::getId).endsWith("ui");
        assertThat(ordered.stream().filter(route -> route.getId().equals("ui")).findFirst())
                .get()
                .satisfies(ui -> assertThat(ui.getOrder())
                        .as("the catch-all must lose to every API route")
                        .isGreaterThan(routes().stream()
                                .filter(route -> !route.getId().equals("ui"))
                                .mapToInt(Route::getOrder)
                                .max().orElseThrow()));
    }

    @Test
    void sendsSessionAndGameTrafficToDifferentServices() {
        assertThat(routes()).filteredOn(route -> route.getId().equals("sessions"))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getPort()).isEqualTo(8082));

        assertThat(routes()).filteredOn(route -> route.getId().equals("games"))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getPort()).isEqualTo(8081));
    }
}
