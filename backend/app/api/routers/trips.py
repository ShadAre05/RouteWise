import uuid

from fastapi import APIRouter, Depends, Query, Response, status

from app.api import deps, schemas
from app.api.schemas import ErrorResponse
from app.core.constants import DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE
from app.services.trip_service import TripRequest, TripService

router = APIRouter(prefix="/trips", tags=["trips"], responses={401: {"model": ErrorResponse}})
NOT_FOUND = {404: {"model": ErrorResponse}}


@router.post(
    "",
    response_model=schemas.TripOut,
    status_code=status.HTTP_201_CREATED,
    summary="Calculate the cost of a trip and save it to the history (US-05 ... US-16)",
    responses={422: {"model": ErrorResponse}, 502: {"model": ErrorResponse}},
)
def create_trip(
    body: schemas.TripCreate, user_id=Depends(deps.user_id_of), service: TripService = Depends(deps.trip_service)
):
    request = TripRequest(
        origin_label=body.origin_label,
        destination_label=body.destination_label,
        origin=body.origin.as_tuple(),
        destination=body.destination.as_tuple(),
        variant=body.variant,
        vehicle_id=body.vehicle_id,
        daily_limit_h=body.daily_limit_h,
        lodging_class=body.lodging_class,
        lodging_entered_eur=body.lodging_entered_eur,
        start_fuel=body.start_fuel,
        title=body.title,
    )
    return service.calculate_and_save(user_id, request)


@router.get("", response_model=schemas.TripPage, summary="Trip history, newest first (US-17)")
def list_trips(
    limit: int = Query(DEFAULT_PAGE_SIZE, ge=1, le=MAX_PAGE_SIZE),
    offset: int = Query(0, ge=0),
    user_id=Depends(deps.user_id_of),
    service: TripService = Depends(deps.trip_service),
):
    items, total = service.list(user_id, limit, offset)
    return schemas.TripPage(items=items, total=total, limit=limit, offset=offset)


@router.get("/{trip_id}", response_model=schemas.TripOut, responses=NOT_FOUND, summary="Trip details")
def get_trip(trip_id: uuid.UUID, service: TripService = Depends(deps.trip_service)):
    return service.get(trip_id)


@router.patch("/{trip_id}", response_model=schemas.TripOut, responses=NOT_FOUND, summary="Rename a saved trip")
def rename_trip(trip_id: uuid.UUID, body: schemas.TripRename, service: TripService = Depends(deps.trip_service)):
    return service.rename(trip_id, body.title)


@router.delete(
    "/{trip_id}", status_code=status.HTTP_204_NO_CONTENT, responses=NOT_FOUND, summary="Delete a trip (US-18)"
)
def delete_trip(trip_id: uuid.UUID, service: TripService = Depends(deps.trip_service)):
    service.delete(trip_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
