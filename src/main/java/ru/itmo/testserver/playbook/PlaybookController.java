package ru.itmo.testserver.playbook;

import java.util.Collection;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chaos/playbooks")
public class PlaybookController {

    private final PlaybookRunner runner;

    public PlaybookController(PlaybookRunner runner) {
        this.runner = runner;
    }

    @GetMapping
    public Collection<Playbook> list() {
        return runner.list().values();
    }

    @PostMapping("/{name}/run")
    public PlaybookRunner.RunView run(@PathVariable String name) {
        return runner.start(name);
    }

    @PostMapping("/stop")
    public ResponseEntity<PlaybookRunner.RunView> stop() {
        PlaybookRunner.RunView view = runner.stop();
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    @GetMapping("/current")
    public ResponseEntity<PlaybookRunner.RunView> current() {
        PlaybookRunner.RunView view = runner.current();
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }
}
