package com.fitech.app.users.domain.services.impl;

import com.fitech.app.commons.util.MapperUtil;
import com.fitech.app.commons.util.PaginationUtil;
import com.fitech.app.users.domain.model.LoginResponseDto;
import com.fitech.app.users.domain.entities.Person;
import com.fitech.app.users.domain.model.PersonDto;
import com.fitech.app.users.domain.model.UserLoginRequest;
import com.fitech.app.users.domain.services.PersonService;
import com.fitech.app.users.domain.services.UserService;
import com.fitech.app.users.domain.entities.User;
import com.fitech.app.users.infrastructure.repository.UserRepository;
import com.fitech.app.users.application.dto.ResultPage;
import com.fitech.app.users.application.exception.InvalidPasswordException;
import com.fitech.app.users.application.exception.UserNotFoundException;
import com.fitech.app.users.application.exception.DuplicatedUserException;
import com.fitech.app.users.domain.model.UserDto;
import com.fitech.app.users.domain.model.UserResponseDto;
import com.fitech.app.users.infrastructure.security.JwtTokenProvider;
import com.fitech.app.users.infrastructure.security.PasswordEncoderUtil;
import com.fitech.app.users.infrastructure.email.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fitech.app.users.application.exception.EmailNotVerifiedException;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PersonService personService;
    private final PasswordEncoderUtil passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    public UserServiceImpl(UserRepository userRepository,
                           PersonService personService,
                           PasswordEncoderUtil passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.personService = personService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public UserDto save(UserDto userDto) {
        validateUserCreation(userDto);
        Person person = personService.save(userDto.getPerson());
        
        // Create and configure the User entity
        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setType(userDto.getType());
        user.setPerson(person);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        generateVerificationToken(user);
        
        User savedUser = userRepository.save(user);

        // Enviar email de verificación en un hilo separado
        try {
            emailService.sendVerificationEmail(person.getEmail(), user.getEmailVerificationToken());
        } catch (IOException e) {
            // Log the error but don't fail the registration
            // The user can request a new verification email later
            log.error("Error sending verification email to " + person.getEmail(), e);
        }

        return MapperUtil.map(savedUser, UserDto.class);
    }

    private void validateUserCreation(UserDto userDto) {
        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new DuplicatedUserException("Username already exists: " + userDto.getUsername());
        }
    }

    private void generateVerificationToken(User user) {
        String token = UUID.randomUUID().toString();
        user.setEmailVerificationToken(token);
        user.setEmailTokenExpiresAt(LocalDateTime.now().plusHours(24));
        user.setIsEmailVerified(false);
    }

    @Override
    @Transactional
    public UserDto update(Integer id, UserDto userDto) {
        validateUserUpdate(id, userDto);
        User user = updateUserEntity(id, userDto);
        User savedUser = userRepository.save(user);
        return MapperUtil.map(savedUser, UserDto.class);
    }

    private void validateUserUpdate(Integer id, UserDto userDto) {
        User existingUser = getUserEntityById(id);
        
        if (userDto.hasDifferentUserName(existingUser.getUsername())) {
            if (usernameAlreadyExistsByOrgId(userDto.getUsername(), null)) {
                throw new DuplicatedUserException("Username already exists: " + userDto.getUsername());
            }
        }
    }

    private User updateUserEntity(Integer id, UserDto userDto) {
        // Get the existing user entity
        User existingUser = getUserEntityById(id);
        
        // Update the Person entity if needed
        if (userDto.getPerson() != null) {
            PersonDto updatedPerson = personService.update(existingUser.getPerson().getId(), userDto.getPerson());
            existingUser.setPerson(MapperUtil.map(updatedPerson, Person.class));
        }
        
        // Map the DTO to the existing entity
        MapperUtil.map(userDto, existingUser);
        existingUser.setId(id);  // Ensure ID is preserved
        existingUser.setUpdatedAt(LocalDateTime.now());
        
        return existingUser;
    }

    @Override
    public UserResponseDto getByUsernameAndPassword(UserLoginRequest loginRequest) {
        Optional<User> user = userRepository.
                findByUsernameAndPassword(loginRequest.getUsername(), loginRequest.getPassword());
        if(user.isEmpty()) {
            throw new InvalidPasswordException("Invalid credentials for username: " + loginRequest.getUsername());
        }
        return MapperUtil.map(user.get(), UserResponseDto.class);
    }

    @Override
    public Boolean usernameAlreadyExistsByOrgId(String username, Integer orgId) {
        Optional<User> user = userRepository.
                findByUsername(username);
        return user.isPresent();
    }

    @Override
    public UserResponseDto getById(Integer id){
        Optional<User> user = userRepository.findById(id);
        if(user.isEmpty()){
            throw new UserNotFoundException("User not found with ID: " + id);
        }
        return MapperUtil.map(user.get(), UserResponseDto.class);
    }

    public User getUserEntityById(Integer usedId) {
        Optional<User> existingUser = userRepository.findById(usedId);
        return existingUser.orElseThrow(() -> new UserNotFoundException("User not found with ID: " + usedId));
    }
    
    @Override
    public ResultPage<UserResponseDto> getAll(Pageable paging){
        Page<User> users = userRepository.findAll(paging);
        if(users.isEmpty()){
            throw new UserNotFoundException("No users found");
        }
        return PaginationUtil.prepareResultWrapper(users, UserResponseDto.class);
    }

    @Override
    @Transactional
    public UserResponseDto verifyEmail(String token) throws Exception {
        User user = userRepository.findByEmailVerificationToken(token)
            .orElseThrow(() -> new Exception("Token de verificación inválido"));

        if (user.getEmailTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new Exception("Token de verificación expirado");
        }

        user.setIsEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailTokenExpiresAt(null);
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        return MapperUtil.map(updatedUser, UserResponseDto.class);
    }

    @Override
    public LoginResponseDto login(String username, String password) {
        if (username == null || password == null) {
            throw new InvalidPasswordException("Usuario y contraseña son requeridos");
        }

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new InvalidPasswordException("Usuario o contraseña incorrectos"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidPasswordException("Usuario o contraseña incorrectos");
        }

        if (!user.getIsEmailVerified()) {
            throw new EmailNotVerifiedException("Tu cuenta no está verificada. Por favor revisa tu correo electrónico para verificar tu cuenta antes de iniciar sesión");
        }

        String token = jwtTokenProvider.generateToken(username);
        
        LoginResponseDto response = new LoginResponseDto();
        response.setToken(token);
        response.setUser(MapperUtil.map(user, UserResponseDto.class));
        
        return response;
    }

    @Override
    public Boolean emailAlreadyExists(String email) {
        return userRepository.existsByPersonEmail(email);
    }
}
