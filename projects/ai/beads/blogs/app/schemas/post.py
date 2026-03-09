from app.extensions import ma
from app.models.post import Post


class PostSchema(ma.SQLAlchemyAutoSchema):
    class Meta:
        model = Post
        load_instance = True
        include_fk = True

    title = ma.auto_field(required=True)
    body = ma.auto_field(required=True)


post_schema = PostSchema()
posts_schema = PostSchema(many=True)
