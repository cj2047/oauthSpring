package com.zjzhd.yfb.oauth;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.common.DefaultExpiringOAuth2RefreshToken;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.ExpiringOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.InvalidScopeException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.token.AuthorizationServerTokenServices;
import org.springframework.security.oauth2.provider.token.ConsumerTokenServices;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.util.Assert;

public class OAuth2TokenService
    implements AuthorizationServerTokenServices,ResourceServerTokenServices,ConsumerTokenServices,InitializingBean {

	// refresh_token 的超时时间 默认2592000秒
	private int refreshTokenValiditySeconds = 2592000;
	// access_token 的超时时间 默认12个小时
	private int accessTokenValiditySeconds = 43200;
	// 是否支持access_token 刷新，默认是false,在配置文件中以配置成可以支持了
	private boolean supportRefreshToken = false;
	// 使用refresh_token刷新之后该refresh_token是否依然使用，默认是依然使用
	private boolean reuseRefreshToken = true;
	// access_token的存储方式，这个在配置文件中配置
	private TokenStore tokenStore;

	private ClientDetailsService	clientDetailsService;
	private TokenEnhancer					accessTokenEnhancer;

	public OAuth2TokenService() {}

	public void afterPropertiesSet() throws Exception{
		Assert.notNull(this.tokenStore,"tokenStore must be set");
	}

	public boolean revokeToken(String tokenValue){
		OAuth2AccessToken accessToken = this.tokenStore.readAccessToken(tokenValue);
		if(accessToken == null)
			return false;
		else{
			if(accessToken.getRefreshToken() != null){
				this.tokenStore.removeRefreshToken(accessToken.getRefreshToken());
			}
			this.tokenStore.removeAccessToken(accessToken);
			return true;
		}
	}

	public OAuth2Authentication loadAuthentication(String accessTokenValue)
	    throws AuthenticationException,InvalidTokenException{
		OAuth2AccessToken accessToken = this.tokenStore.readAccessToken(accessTokenValue);
		if(accessToken == null){
			throw new InvalidTokenException("Invalid access token: " + accessTokenValue);
		}else if(accessToken.isExpired()){
			this.tokenStore.removeAccessToken(accessToken);
			throw new InvalidTokenException("Access token expired: " + accessTokenValue);
		}else{
			OAuth2Authentication result = this.tokenStore.readAuthentication(accessToken);
			if(this.clientDetailsService != null){
				String clientId = result.getOAuth2Request().getClientId();
				try{
					this.clientDetailsService.loadClientByClientId(clientId);
				}catch(ClientRegistrationException cre){
					throw new InvalidTokenException("Client not valid: " + clientId,cre);
				}
			}
			return result;
		}
	}

	public OAuth2AccessToken readAccessToken(String accessToken){
		return this.tokenStore.readAccessToken(accessToken);
	}

	public OAuth2AccessToken createAccessToken(OAuth2Authentication authentication) throws AuthenticationException{
		OAuth2AccessToken existingAccessToken = this.tokenStore.getAccessToken(authentication);
		Object refreshToken = null;
		if(existingAccessToken != null){
			if(!existingAccessToken.isExpired()){
				return existingAccessToken;
			}
			if(existingAccessToken.getRefreshToken() != null){
				refreshToken = existingAccessToken.getRefreshToken();
				this.tokenStore.removeRefreshToken((OAuth2RefreshToken)refreshToken);
			}
			this.tokenStore.removeAccessToken(existingAccessToken);
		}

		if(refreshToken == null){
			refreshToken = this.createRefreshToken(authentication);
		}else if(refreshToken instanceof ExpiringOAuth2RefreshToken){
			ExpiringOAuth2RefreshToken token = (ExpiringOAuth2RefreshToken)refreshToken;
			if(System.currentTimeMillis() > token.getExpiration().getTime()){
				refreshToken = this.createRefreshToken(authentication);
			}
		}

		OAuth2AccessToken accessToken = this.createAccessToken(authentication,(OAuth2RefreshToken)refreshToken);
		this.tokenStore.storeAccessToken(accessToken,authentication);
		OAuth2RefreshToken refreshToken_new = accessToken.getRefreshToken();
		if(refreshToken_new != null){
			this.tokenStore.storeRefreshToken(refreshToken_new,authentication);
		}
		return accessToken;
	}

	public OAuth2AccessToken getAccessToken(OAuth2Authentication authentication){
		return this.tokenStore.getAccessToken(authentication);
	}

	public OAuth2AccessToken refreshAccessToken(String refreshTokenValue, TokenRequest tokenRequest)
	    throws AuthenticationException{
		if(!this.supportRefreshToken){
			throw new InvalidGrantException("Invalid refresh token: " + refreshTokenValue);
		}else{
			Object refreshToken = this.tokenStore.readRefreshToken(refreshTokenValue);
			if(refreshToken == null){
				throw new InvalidGrantException("Invalid refresh token: " + refreshTokenValue);
			}else{
				OAuth2Authentication authentication = this.tokenStore
				    .readAuthenticationForRefreshToken((OAuth2RefreshToken)refreshToken);
				String clientId = authentication.getOAuth2Request().getClientId();
				if(clientId != null && clientId.equals(tokenRequest.getClientId())){
					this.tokenStore.removeAccessTokenUsingRefreshToken((OAuth2RefreshToken)refreshToken);
					if(this.isExpired((OAuth2RefreshToken)refreshToken)){
						this.tokenStore.removeRefreshToken((OAuth2RefreshToken)refreshToken);
						throw new InvalidTokenException("Invalid refresh token(expired: " + refreshToken);
					}else{
						authentication=this.createRefreshedAuthentication(authentication,tokenRequest.getScope());
						if(!this.reuseRefreshToken){
							this.tokenStore.removeRefreshToken((OAuth2RefreshToken)refreshToken);
							refreshToken=this.createRefreshToken(authentication);
						}
						
						OAuth2AccessToken accessToken=this.createAccessToken(authentication,(OAuth2RefreshToken)refreshToken);
						this.tokenStore.storeAccessToken(accessToken,authentication);
						if(!this.reuseRefreshToken){
							this.tokenStore.storeRefreshToken((OAuth2RefreshToken)refreshToken,authentication);
						}
						
						return accessToken;
					}
				}else{
					throw new InvalidGrantException("Wrong client for this refresh token: " + refreshTokenValue);
				}
			}
		}
	}

	private OAuth2Authentication createRefreshedAuthentication(OAuth2Authentication authentication, Set<String> scope){
		OAuth2Authentication narrowed = authentication;
		if(scope != null && !scope.isEmpty()){
			OAuth2Request clientAuth = authentication.getOAuth2Request();
			Set originalScope = clientAuth.getScope();
			if(originalScope == null || !originalScope.containsAll(scope)){
				throw new InvalidScopeException("Unable to narrow the scope of the client authentication to " + scope + ".",
				    originalScope);
			}
			narrowed = new OAuth2Authentication(clientAuth.narrowScope(scope),authentication.getUserAuthentication());
		}

		return narrowed;
	}

	private OAuth2AccessToken createAccessToken(OAuth2Authentication authentication, OAuth2RefreshToken refreshToken){
		String access_token = UUID.randomUUID().toString().replaceAll("-","");
		System.out.println("\n\n-----------------------------------\n\n\n");
		System.out.println("-----------------------------------Access_token = " + access_token);
		System.out.println("\n\n-----------------------------------\n\n\n");
		DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken(access_token);
		int validitySeconds = this.getAccessTokenValiditySeconds(authentication.getOAuth2Request());

		System.out.println("\n\n-----------------------------------\n\n\n");
		System.out.println("-----------------------------------" + validitySeconds);
		System.out.println("\n\n-----------------------------------\n\n\n");

		if(validitySeconds > 0){
			token.setExpiration(new Date(System.currentTimeMillis() + (long)validitySeconds * 1000L));
		}

		token.setRefreshToken(refreshToken);
		token.setScope(authentication.getOAuth2Request().getScope());
		return (OAuth2AccessToken)(this.accessTokenEnhancer != null ? this.accessTokenEnhancer.enhance(token,authentication)
		    : token);

	}

	private ExpiringOAuth2RefreshToken createRefreshToken(OAuth2Authentication authentication){
		if(!this.isSupportRefreshToken(authentication.getOAuth2Request())){
			return null;
		}else{
			int validitySeconds = this.getRefreshTokenValiditySeconds(authentication.getOAuth2Request());

			String access_tokens = UUID.randomUUID().toString().replaceAll("-","");
			System.out.println("\n\n-----------------------------------\n\n\n");
			System.out.println("-----------------------------------refreshAccess_token = " + access_tokens);
			System.out.println("\n\n-----------------------------------\n\n\n");
			DefaultExpiringOAuth2RefreshToken refreshToken = new DefaultExpiringOAuth2RefreshToken(access_tokens,
			    new Date(System.currentTimeMillis() + (long)validitySeconds * 1000L));
			return refreshToken;
		}
	}

	protected boolean isExpired(OAuth2RefreshToken refreshToken){
		if(!(refreshToken instanceof ExpiringOAuth2RefreshToken)){
			return false;
		}else{
			ExpiringOAuth2RefreshToken expiringToken = (ExpiringOAuth2RefreshToken)refreshToken;
			return expiringToken.getExpiration() == null
			    || System.currentTimeMillis() > expiringToken.getExpiration().getTime();
		}

	}

	protected boolean isSupportRefreshToken(OAuth2Request clientAuth){
		if(this.clientDetailsService != null){
			ClientDetails client = this.clientDetailsService.loadClientByClientId(clientAuth.getClientId());
			return client.getAuthorizedGrantTypes().contains("refresh_token");
		}else{
			return this.supportRefreshToken;
		}
	}

	protected int getAccessTokenValiditySeconds(OAuth2Request clientAuth){
		if(this.clientDetailsService != null){
			ClientDetails client = this.clientDetailsService.loadClientByClientId(clientAuth.getClientId());
			Integer validity = client.getAccessTokenValiditySeconds();
			if(validity != null){
				return validity.intValue();
			}
		}

		return this.accessTokenValiditySeconds;
	}

	protected int getRefreshTokenValiditySeconds(OAuth2Request clientAuth){
		if(this.clientDetailsService != null){
			ClientDetails client = this.clientDetailsService.loadClientByClientId(clientAuth.getClientId());
			Integer validity = client.getRefreshTokenValiditySeconds();
			if(validity != null){
				return validity.intValue();
			}
		}

		return this.refreshTokenValiditySeconds;
	}

	public void setRefreshTokenValiditySeconds(int refreshTokenValiditySeconds){
		this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
	}

	public void setAccessTokenValiditySeconds(int accessTokenValiditySeconds){
		this.accessTokenValiditySeconds = accessTokenValiditySeconds;
	}

	public void setSupportRefreshToken(boolean supportRefreshToken){
		this.supportRefreshToken = supportRefreshToken;
	}

	public void setReuseRefreshToken(boolean reuseRefreshToken){
		this.reuseRefreshToken = reuseRefreshToken;
	}

	public void setTokenStore(TokenStore tokenStore){
		this.tokenStore = tokenStore;
	}

	public void setClientDetailsService(ClientDetailsService clientDetailsService){
		this.clientDetailsService = clientDetailsService;
	}

	public void setAccessTokenEnhancer(TokenEnhancer accessTokenEnhancer){
		this.accessTokenEnhancer = accessTokenEnhancer;
	}

}
