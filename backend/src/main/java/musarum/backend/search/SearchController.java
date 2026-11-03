package musarum.backend.search;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.search.dto.SearchResultDto;

@RestController
@RequestMapping("/search")
class SearchController {

    private final SearchService searchService;

    SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    ResponseEntity<SearchResultDto> search(@RequestParam String q) {
        return ResponseEntity.ok(searchService.search(q));
    }
}