package com.task.global.services;

import com.task.global.entity.Phone;
import com.task.global.entity.User;
import com.task.global.model.UserDTO;
import com.task.global.model.UserResponseDTO;
import com.task.global.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.secret}")
    private String jwtSecret; // Correctamente inyectada desde application.properties

    @Value("${app.validation.password-regex}")
    private String passwordRegex; // Expresión regular para la validación de la contraseña

    private static final String EMAIL_REGEX = "^\\w+([-+.']\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$"; // Expresión regular para el correo electrónico

    public UserResponseDTO registerUser(User user2) {
        // Validación del formato del correo electrónico
        if (!Pattern.matches(EMAIL_REGEX, user2.getEmail())) {
            throw new IllegalArgumentException("Formato de correo electrónico inválido.");
        }

        // Validación del formato de la contraseña
        if (!Pattern.matches(passwordRegex, user2.getPassword())) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres con una mezcla de números y letras.");
        }

        // Verificar si el correo ya está registrado
        if (userRepository.existsByEmail(user2.getEmail())) {
            throw new RuntimeException("El correo ya está registrado.");
        }

        // Construcción del usuario
        User user = new User();
        user.setName(user2.getName());
        user.setEmail(user2.getEmail());
        user.setPassword(user2.getPassword()); // Considerar el uso de cifrado para la contraseña
        LocalDateTime now = LocalDateTime.now();
        user.setCreated(now);
        user.setModified(now);
        user.setLastLogin(now);
        user.setActive(true);
        user.setToken(generateToken(user)); // Generación del token JWT

        // Asignación de teléfonos al usuario
        Set<Phone> phones = user2.getPhones().stream().map(phoneDTO -> {
            Phone phone = new Phone();
            phone.setNumber(phoneDTO.getNumber());
            phone.setCitycode(phoneDTO.getCitycode());
            phone.setCountrycode(phoneDTO.getCountrycode());
            phone.setUser(user);
            return phone;
        }).collect(Collectors.toSet());

        user.setPhones(phones);

        try {
            userRepository.save(user); // Guardado del usuario en la base de datos
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Error al registrar el usuario, posiblemente el correo ya esté en uso.");
        }

        // Preparar y devolver la respuesta
        return prepareUserResponseDTO(user);
    }

    private String generateToken(User user) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(now)
                .signWith(SignatureAlgorithm.HS512, jwtSecret) // Uso de la clave secreta para firmar el token
                .compact();
    }

    private UserResponseDTO prepareUserResponseDTO(User user) {
        UserResponseDTO response = new UserResponseDTO();
        response.setId(user.getId().toString());
        response.setCreated(user.getCreated());
        response.setModified(user.getModified());
        response.setLastLogin(user.getLastLogin());
        response.setToken(user.getToken());
        response.setActive(user.isActive());
        response.setMessage("Usuario creado exitosamente");
        return response;
    }
}
