package com.zjzhd.yfb.mvc;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.zjzhd.yfb.model.User;
import com.zjzhd.yfb.service.UserService;

@Controller
@RequestMapping("/api")
public class HomeController {

	@Autowired
	private UserService	userService;
	@Autowired
	private TokenStore	tokenStore;

	@RequestMapping(value = "/web/user", method = RequestMethod.GET)
	@ResponseBody
	public User getUserByAccessToken(HttpServletRequest request){
		String accessTokenValue = request.getParameter("access_token");
		OAuth2Authentication authentication = this.tokenStore.readAuthentication(accessTokenValue);

		return userService.getUserByName(authentication.getName());

	}

	@RequestMapping(value = "/admin/users", method = RequestMethod.GET)
	@ResponseBody
	public List<User> getAllUsers(){
		return userService.getAllUsers();
	}

	@RequestMapping(value = "/admin/user/{name}", method = RequestMethod.GET)
	@ResponseBody
	public User findUserByName(@PathVariable String name){

		return userService.getUserByName(name);

	}

}
