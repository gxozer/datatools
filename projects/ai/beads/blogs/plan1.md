# Flask Blog REST API — Implementation Plan

## Context
Building a REST API for a blog from scratch. The project currently has only PyCharm boilerplate in `main.py`. Stack: Flask + SQLite (via SQLAlchemy) + JWT auth.

## File Structure

```
beads1/
├── main.py                    # Replace boilerplate — app entry point
├── requirements.txt           # Generated after pip install
├── .env                       # Secrets (not committed)
└── app/
    ├── __init__.py            # App factory create_app()
    ├── config.py              # Config classes
    ├── extensions.py          # db, jwt, ma singletons (avoids circular imports)
    ├── models/
    │   ├── __init__.py        # Import all models so db.create_all() sees them
    │   ├── user.py
    │   ├── post.py
    │   └── comment.py
    ├── schemas/
    │   ├── __init__.py
    │   ├── user.py
    │   ├── post.py
    │   └── comment.py
    └── routes/
        ├── __init__.py
        ├── auth.py            # /auth/register, /auth/login, /auth/refresh
        ├── posts.py           # /posts CRUD
        └── comments.py        # /posts/<id>/comments CRUD
```

## Dependencies

```
flask>=3.0.0
flask-sqlalchemy>=3.1.0
flask-jwt-extended>=4.6.0
flask-marshmallow>=1.2.0
marshmallow-sqlalchemy>=1.1.0
python-dotenv>=1.0.0
bcrypt>=4.1.0
```

## Data Models

**User**: id, username (unique), email (unique), password_hash, created_at
**Post**: id, title, body, created_at, updated_at, author_id (FK→users)
**Comment**: id, body, created_at, updated_at, post_id (FK→posts), author_id (FK→users)

- Deleting a post cascades to delete its comments (`cascade="all, delete-orphan"`)

## API Endpoints

### Auth
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | No | Create account |
| POST | `/auth/login` | No | Returns access + refresh tokens |
| POST | `/auth/refresh` | Refresh token | New access token |

### Posts
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/posts/` | No | List posts (paginated) |
| POST | `/posts/` | JWT | Create post |
| GET | `/posts/<id>` | No | Get post |
| PUT | `/posts/<id>` | JWT + owner | Update post |
| DELETE | `/posts/<id>` | JWT + owner | Delete post |

### Comments
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/posts/<post_id>/comments` | No | List comments |
| POST | `/posts/<post_id>/comments` | JWT | Add comment |
| PUT | `/posts/<post_id>/comments/<id>` | JWT + owner | Update comment |
| DELETE | `/posts/<post_id>/comments/<id>` | JWT + owner | Delete comment |

## Implementation Order

1. Install deps, freeze requirements.txt
2. Create `app/extensions.py` (db, jwt, ma — imported everywhere else)
3. Create `app/config.py` + `.env`
4. Create models: user → post → comment → `models/__init__.py`
5. Create marshmallow schemas (user, post, comment)
6. Create `app/__init__.py` (app factory) + replace `main.py`
7. Implement `routes/auth.py` and verify JWT works
8. Implement `routes/posts.py`
9. Implement `routes/comments.py`
10. Add global JSON error handlers (400, 401, 403, 404, 422, 500)

## Key Patterns

- **App factory**: `create_app()` in `app/__init__.py` — avoids circular imports
- **Owner check**: `if resource.author_id != get_jwt_identity(): abort(403)`
- **Validation**: marshmallow `.validate()` before DB writes, return 422 on errors
- **Pagination**: `Post.query.paginate(page=page, per_page=20)`

## Verification

```bash
source .venv/bin/activate
python main.py

# Register + login
curl -X POST http://localhost:5000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@ex.com","password":"secret123"}'

TOKEN=$(curl -s -X POST http://localhost:5000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@ex.com","password":"secret123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Create post, list posts, add comment
curl -X POST http://localhost:5000/posts/ \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Hello","body":"First post"}'

curl http://localhost:5000/posts/
```
