package com.popcornjournal.service;

import com.popcornjournal.dto.ReferenceDtos.WatchlistItemDto;
import com.popcornjournal.dto.ReferenceDtos.WatchlistItemRequest;
import com.popcornjournal.entity.WatchlistItem;
import com.popcornjournal.exception.NotFoundException;
import com.popcornjournal.mapper.MovieMapper;
import com.popcornjournal.repository.WatchlistItemRepository;
import com.popcornjournal.support.Titles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Titles recommended to watch later. Kept apart from movies so they never count as watched. */
@Service
@Transactional
public class WatchlistService {

    private final WatchlistItemRepository items;
    private final ReferenceDataService referenceData;
    private final MovieMapper mapper;

    public WatchlistService(WatchlistItemRepository items, ReferenceDataService referenceData, MovieMapper mapper) {
        this.items = items;
        this.referenceData = referenceData;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WatchlistItemDto> list() {
        return items.findAllByOrderByCreatedAtDescIdDesc().stream().map(mapper::toDto).toList();
    }

    public WatchlistItemDto create(WatchlistItemRequest request) {
        WatchlistItem item = new WatchlistItem();
        apply(request, item);
        return mapper.toDto(items.save(item));
    }

    public WatchlistItemDto update(Long id, WatchlistItemRequest request) {
        WatchlistItem item = items.findById(id).orElseThrow(() -> new NotFoundException("Watchlist item", id));
        apply(request, item);
        items.flush();
        return mapper.toDto(item);
    }

    public void delete(Long id) {
        WatchlistItem item = items.findById(id).orElseThrow(() -> new NotFoundException("Watchlist item", id));
        items.delete(item);
    }

    private void apply(WatchlistItemRequest request, WatchlistItem item) {
        item.setTitle(Titles.clean(request.title()));
        item.setRecommendedBy(Titles.clean(request.recommendedBy()));
        item.setNotes(MovieMapper.blankToNull(request.notes()));
        String genre = Titles.clean(request.genre());
        item.setGenre(genre == null ? null : referenceData.resolveGenres(List.of(genre)).iterator().next());
    }
}
