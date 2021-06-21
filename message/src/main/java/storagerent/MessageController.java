package storagerent;


import org.springframework.web.bind.annotation.RestController;
import storagerent.external.StorageService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MessageController {
    private StorageService storageService;

    public MessageController(StorageService storageService){
        this.storageService = storageService;
    }

    @GetMapping("/checkStorage")
    String chkAndReqReserve() {
        return storageService.checkStorage();
    }
}
