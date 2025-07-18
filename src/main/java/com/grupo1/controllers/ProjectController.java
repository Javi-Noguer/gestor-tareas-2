package com.grupo1.controllers;

import com.grupo1.entities.Project;
import com.grupo1.entities.User;
import com.grupo1.repositories.ProjectRepository;
import com.grupo1.repositories.TaskRepository;
import com.grupo1.repositories.UserRepository;
import com.grupo1.services.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Controller
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;


    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    //se dejan de mostrar todos, para solo mostrar los que pertenecen al usuario
    @GetMapping
    public String getProjects(Model model, Principal principal) {
        String loggedUsername = principal.getName();
        User user = userRepository.findByUsername(loggedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<Project> userProjects = user.getProjects();
        model.addAttribute("loggedUser", user);
        model.addAttribute("projects", userProjects);
        return "/project/project-list";
    }

    // Ver detalles de un proyecto por ID
    @GetMapping("/{id}")
    public String findById(Model model, Principal principal, @PathVariable Long id) {
        String loggedUsername = principal.getName();
        User user = userRepository.findByUsername(loggedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Optional<Project> projectOpt = projectRepository.findById(id);
        if (projectOpt.isEmpty()) {
            model.addAttribute("error", "Proyecto no encontrado");
           return "redirect:/projects";
        }
        Project project = projectOpt.get();
        if (!project.getUsers().contains(user)) {
            model.addAttribute("error", "No tienes acceso");
            return "redirect:/projects";

        }
        model.addAttribute("loggedUser", user);
        model.addAttribute("project", projectOpt.get());
        return "project/project-detail";
    }

    // Formulario para crear nuevo proyecto
    @GetMapping("/new")
    public String createForm(Model model, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Project newProject = new Project();
        newProject.setStartDate(LocalDate.now());
        model.addAttribute("loggedUser", user);
        model.addAttribute("project",newProject);
        return "/project/project-form";
    }

    // Guardar proyecto (nuevo o editado)
    @PostMapping("/save")
    public String saveForm(@ModelAttribute Project newProject, Principal principal) {
        String username = principal.getName();
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return "redirect:/login";
        }

        User user = userOptional.get();
        Project project;


        if (newProject.getId() != null) {
            project = projectRepository.findById(newProject.getId()).orElseThrow(
                    () -> new IllegalArgumentException("proyecto no encontrado"));
            project.setName(newProject.getName());
            project.setDescription(newProject.getDescription());
            project.setStartDate(newProject.getStartDate());
        } else {
            // Si es nuevo, asignar datos
            project = newProject;
            project.setCreatedBy(user);
        }

        //asignar usuario creador a proyecto
        if (!project.getUsers().contains(user)) {
            project.getUsers().add(user);
        }

       //misma relacion, invertida
        if (!user.getProjects().contains(project)) {
            user.getProjects().add(project);
        }


        projectRepository.save(project);

        return "redirect:/projects";
    }


    @GetMapping("/{id}/editar")
    public String editar(Model model, @PathVariable Long id, Principal principal) {
        String loggedUsername = principal.getName();
        User user = userRepository.findByUsername(loggedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Optional<Project> project = projectRepository.findById(id);
        List<User> allUsers = userRepository.findAll();
        if (project.isPresent()) {
            model.addAttribute("allUsers", allUsers);
            model.addAttribute("loggedUser", user);
            model.addAttribute("project", project.get());
            return "/project/project-form";
        } else {
            model.addAttribute("error", "Proyecto no encontrado");
            return "redirect:/projects";
        }
    }

    // Eliminar proyecto
    @PostMapping("/{id}/eliminar")
    public String delete(@PathVariable Long id) {
        Project project = projectRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Proyecto no encontrado"));

        for (User user : project.getUsers()) {
            user.getProjects().remove(project);
        }

        project.getUsers().clear();

        projectRepository.delete(project);

        return "redirect:/projects";
    }

    @PostMapping("/{id}/addUser")
    public String addUserToProject(@PathVariable Long id, @RequestParam UUID userId, Principal principal) {
        Project project = projectRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Proyecto no encontrado"));

        User addedUser = userRepository.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("Usuario no encontrado"));
        //evitar que se pueda añadir a si mismo y a ya existentes hecho en vista
        //si no lo contiene ya, añadir usuario seleccionado al proyecto

        if (!project.getUsers().contains(addedUser)) {
            project.getUsers().add(addedUser);
            addedUser.getProjects().add(project);
            projectRepository.save(project);
        }
        return "redirect:/projects/" + id;
    }

}
