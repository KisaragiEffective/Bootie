CREATE TABLE booth_items (
    id BIGSERIAL PRIMARY KEY,
    booth_item_id VARCHAR(255) NOT NULL UNIQUE,
    name TEXT NOT NULL,
    url TEXT NOT NULL,
    thumbnail_url TEXT,
    shop_name TEXT,
    price TEXT,
    tags TEXT[],
    scraped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_to_misskey BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_booth_items_booth_item_id ON booth_items(booth_item_id);
CREATE INDEX idx_booth_items_posted_to_misskey ON booth_items(posted_to_misskey);
CREATE INDEX idx_booth_items_scraped_at ON booth_items(scraped_at DESC);
