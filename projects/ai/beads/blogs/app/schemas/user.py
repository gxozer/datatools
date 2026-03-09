from app.extensions import ma
from app.models.user import User


class UserSchema(ma.SQLAlchemyAutoSchema):
    class Meta:
        model = User
        load_instance = True
        exclude = ("password_hash",)

    username = ma.auto_field(required=True)
    email = ma.auto_field(required=True)


user_schema = UserSchema()
users_schema = UserSchema(many=True)
