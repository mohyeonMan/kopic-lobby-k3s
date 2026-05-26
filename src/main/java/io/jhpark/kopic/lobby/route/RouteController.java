package io.jhpark.kopic.lobby.route;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routes")
public class RouteController {

	private final RouteService routeService;

	public RouteController(RouteService routeService) {
		this.routeService = routeService;
	}

	@PostMapping("/quick")
	public RouteResponse quick(@RequestBody(required = false) RouteRequest.NicknameOnly request) {
		return routeService.quick(request);
	}

	@PostMapping("/private")
	public RouteResponse privateCreate(@RequestBody(required = false) RouteRequest.NicknameOnly request) {
		return routeService.privateCreate(request);
	}

	@PostMapping("/private/join")
	public RouteResponse privateJoin(@RequestBody(required = false) RouteRequest.PrivateJoin request) {
		return routeService.privateJoin(request);
	}
}
