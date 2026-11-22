import uuid

from fastapi import APIRouter, Depends, Response, status

from app.api import deps, schemas
from app.api.schemas import ErrorResponse
from app.services.vehicle_service import VehicleService

router = APIRouter(prefix="/vehicles", tags=["vehicles"], responses={401: {"model": ErrorResponse}})
NOT_FOUND = {404: {"model": ErrorResponse}}


@router.get("", response_model=list[schemas.VehicleOut], summary="List my vehicles")
def list_vehicles(user_id=Depends(deps.user_id_of), service: VehicleService = Depends(deps.vehicle_service)):
    return service.list(user_id)


@router.post(
    "", response_model=schemas.VehicleOut, status_code=status.HTTP_201_CREATED, summary="Add a vehicle (US-03)"
)
def create_vehicle(
    body: schemas.VehicleCreate,
    user_id=Depends(deps.user_id_of),
    service: VehicleService = Depends(deps.vehicle_service),
):
    return service.create(user_id, **body.model_dump())


@router.get("/{vehicle_id}", response_model=schemas.VehicleOut, responses=NOT_FOUND, summary="Get a vehicle")
def get_vehicle(vehicle_id: uuid.UUID, service: VehicleService = Depends(deps.vehicle_service)):
    return service.get(vehicle_id)


@router.patch("/{vehicle_id}", response_model=schemas.VehicleOut, responses=NOT_FOUND, summary="Edit a vehicle")
def update_vehicle(
    vehicle_id: uuid.UUID, body: schemas.VehicleUpdate, service: VehicleService = Depends(deps.vehicle_service)
):
    return service.update(vehicle_id, body.model_dump(exclude_unset=True))


@router.delete(
    "/{vehicle_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    responses=NOT_FOUND,
    summary="Delete a vehicle (its trips stay in the history, US-04)",
)
def delete_vehicle(vehicle_id: uuid.UUID, service: VehicleService = Depends(deps.vehicle_service)):
    service.delete(vehicle_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
