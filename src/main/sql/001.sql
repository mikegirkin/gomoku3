create table users (
    id UUID primary key,
    email varchar not null unique,
    created_at timestamp not null
);

create table games (
    id UUID primary key,
    created_at timestamp not null,
    player_one UUID not null references users(id),
    player_two UUID not null references users(id),
    height integer not null,
    width integer not null,
    win_condition integer not null
);

create table game_moves (
    id UUID primary key,
    game_id UUID not null references games(id),
    seq integer not null,
    created_at timestamp not null,
    row integer not null,
    col integer not null,
    player_number integer check (player_number in (1, 2))
);

create table join_requests (
    id UUID primary key,
    user_id UUID not null references users(id),
    created_at timestamp not null
);

create table game_created (
    id UUID primary key,
    request_left UUID not null references join_requests(id),
    request_right UUID not null references join_requests(id),
    game_id UUID not null references games(id),
    created_at timestamp not null
);