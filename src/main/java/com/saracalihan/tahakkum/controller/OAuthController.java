package com.saracalihan.tahakkum.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.saracalihan.tahakkum.constant.Constants;
import com.saracalihan.tahakkum.constant.TokenStatuses;
import com.saracalihan.tahakkum.constant.TokenTypes;
import com.saracalihan.tahakkum.dto.authentication.LoginDto;
import com.saracalihan.tahakkum.dto.authentication.LoginResponseDto;
import com.saracalihan.tahakkum.dto.oauth.request.AppRegister;
import com.saracalihan.tahakkum.exception.ResponseException;
import com.saracalihan.tahakkum.model.OAuthApp;
import com.saracalihan.tahakkum.model.OAuthToken;
import com.saracalihan.tahakkum.model.Token;
import com.saracalihan.tahakkum.repository.OAuthAppRepository;
import com.saracalihan.tahakkum.repository.OAuthTokenRepository;
import com.saracalihan.tahakkum.repository.TokenRepository;
import com.saracalihan.tahakkum.service.AuthService;
import com.saracalihan.tahakkum.service.TokenService;
import com.saracalihan.tahakkum.utility.Cryptation;
import com.saracalihan.tahakkum.utility.UIBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController()
@RequestMapping("/oauth2")
@Tag(name = "OAuth", description = "Easy login")
public class OAuthController {
    @Autowired
    private OAuthAppRepository oauthAppRepository;
    @Autowired
    OAuthTokenRepository oauthTokenRepository;
    @Autowired
    TokenRepository tokenRepository;

    @Autowired
    private AuthService authService;
    @Autowired
    private TokenService tokenService;

    @Operation(summary = "Register a new OAuth application")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "App registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized request")
    })
    @PostMapping("/register-app")
    public OAuthApp registerApp(@Valid @RequestBody(required = true) AppRegister registerDto, @RequestHeader(name = "x-access-token") String token) throws ResponseException {
        if (oauthAppRepository.existsByName(registerDto.getName())) {
            throw new ResponseException("name already exist!");
        }

        Optional<Token> accessToken = tokenService.findTokenByValue(token);
        if(accessToken.isEmpty()){
            throw new ResponseException("unouthorized!", HttpStatus.UNAUTHORIZED);
        }
        // scope'un alanlarını doğrula
        ArrayList<String> validFields = new ArrayList<>(Arrays.asList(Constants.OAUTH_ACCESSABLE_USER_FIELDS));
        ArrayList<String> invalidScopes = new ArrayList<>();

        for (String s : registerDto.getScopes()) {
            if (!validFields.contains(s)) {
                invalidScopes.add(s);
            }
        }
        if (invalidScopes.size() > 0) {
            StringJoiner invalidJ = new StringJoiner(", ");
            StringJoiner validJ = new StringJoiner(", ");
            for (String s : invalidScopes) {
                invalidJ.add(s);
            }
            for (String s : validFields) {
                validJ.add(s);
            }
            throw new ResponseException(
                    String.format("Hatalı scope değerleri: '%s'. Sadece şu alanlar kullanılabilir: '%s'",
                            invalidJ.toString(), validJ.toString()));
        }

        // redirect url doğrula
        if (!registerDto.getRedirectUrl().startsWith("http://") && !registerDto.getRedirectUrl().startsWith("https://")) {
            throw new ResponseException(
                    String.format("Desteklenmeyen 'redirect_url' adresi: '%s'", registerDto.getRedirectUrl()));
        }

        OAuthApp app = new OAuthApp();
        app.setName(registerDto.getName());
        app.setDescription( registerDto.getDescription());
        app.setHomepage( registerDto.getHomePage());
        app.setPhoto( registerDto.getPhoto());
        app.setRedirectUrl( registerDto.getRedirectUrl());
        app.setScopes( registerDto.getScopes());
        app.setOwner(accessToken.get().getUser());
        app.setClientId(Cryptation.byteToString(Cryptation.createSalt(24)));
        app.setClientSecret(Cryptation.byteToString(Cryptation.createSalt(24)));
        oauthAppRepository.save(app);

        return app;
    }

    @Operation(summary = "Authorize an OAuth application")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Authorization page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid client credentials")
    })
    @GetMapping(value = "/auth", produces = MediaType.TEXT_HTML_VALUE)
    public String oauth(
            @RequestParam(name = "client_id", required = true) String clientId,
            @RequestParam(name = "client_secret", required = true) String clientSecret) {
        // tokens içinden şunları al
        // client id
        // client scret
        // response type -> default access_token
        // scope -> email,password

        // burada arayüz gönderilecek ve arayüz bu controller'daki logine istek atacak
        // orası başarılı olursa gerçek yönlendirme yapılacak
        // bu isteği response_type değerine göre üretilecek kodla birlikte
        // redirect_url'e yönlendir
        // üretilecek token usernama pass giren kullanıcıya ait
        OAuthApp app = oauthAppRepository.findOneByClientId(clientId);
        if (app == null || !app.getClientSecret().equals(clientSecret)) {
            // client_id or client_secret invalid uı
            return "Client id or secret is invalid!";
        }
        Token validationToken = tokenService.createToken(app.getOwner(),
        TokenTypes.OAuthValidate, TokenStatuses.Active, Constants.OAUTH_VALIDATION_TOKEN_TTL_MIN*60L);

        // build html
        // bu sayfa /oauth/login'a tokenla birlikte istek atacak
        return UIBuilder.getInstance().createLogin(app.getName(), app.getDescription(), app.getHomepage(), app.getPhoto(), app.getScopes(), app.getRedirectUrl(), validationToken.getValue());
    }

    @Operation(summary = "Get user info with access token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User info returned"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired access token")
    })
    @GetMapping("/user-info")
    public Map<String, Object> getUser(@RequestHeader(name = "x-access-token", required = true) String accessToken) throws ResponseException {
        OAuthToken token = oauthTokenRepository.findOneByValue(accessToken);
        if(token == null){
            throw new ResponseException("invalid access token!", HttpStatus.UNAUTHORIZED);
        }
        if(token.isExpired(oauthTokenRepository)){
            throw new ResponseException("Access token is expired!",HttpStatus.UNAUTHORIZED);
        }
        OAuthApp app = token.getApp();
        return token.getUser().getFields(app.getScopes());
    }

    @Operation(summary = "Refresh OAuth access token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Access token refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    @PostMapping("/token")
    public void refreshAccessToken() {
        
    }

    @Operation(summary = "Login user and generate access token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User logged in and access token generated"),
        @ApiResponse(responseCode = "400", description = "Invalid login credentials"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired token")
    })
    @PostMapping("/login")
    public RedirectView loginUser(@Valid @RequestBody(required = true) LoginDto loginDto,
            @RequestParam(required = true, name = "token") String t) throws ResponseException {
        // username pass al
        // TODO: urlden gelen isteği sunucunun yaptığına dair bir token al
        // bilgiler doğruysa access_token oluştur ve redirecturleyönlendirme yap.
        Optional<Token> validationToken = tokenService.findTokenByValue(t);
        if(validationToken.isEmpty()){
            throw new ResponseException("invalid token!", HttpStatus.UNAUTHORIZED);
        }

        Token token = validationToken.get();
        if(!token.getType().equals(TokenTypes.OAuthValidate.toString())){
            throw new ResponseException("invalid token!", HttpStatus.UNAUTHORIZED);
        }

        if(!token.getStatus().equals(TokenStatuses.Active.toString())){
            throw new ResponseException("token is expired!", HttpStatus.UNAUTHORIZED);
        }

        Optional<LoginResponseDto> loginInfo =authService.login(loginDto.username, loginDto.password);

        if(loginInfo.isEmpty()){
            throw new ResponseException("username or password is incorrect!");
        }

        token.setStatus(TokenStatuses.Cancelled.toString());
        tokenRepository.save(token);

        OAuthApp app = token.getUser().getOauthApp();
        // if user is valid, create access token for app
        OAuthToken accessToken = tokenService.createOauthToken(app, loginInfo.get().user, t);

        RedirectView redirect = new RedirectView(String.format("%s?access_token=%s", app.getRedirectUrl(), accessToken.getValue()));
        redirect.setStatusCode(HttpStatusCode.valueOf(301));

        return redirect;
    }
}
