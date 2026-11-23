from fastapi import APIRouter, Depends, Query

from app.api import deps, schemas
from app.api.schemas import ErrorResponse
from app.services.reference_service import ReferenceService

router = APIRouter(tags=["reference data"])


@router.get("/countries", response_model=list[schemas.CountryOut], summary="Supported countries")
def countries(service: ReferenceService = Depends(deps.reference_service)):
    """The eight countries the service covers. Public, no token needed."""
    return service.countries()


@router.get("/fuel-prices", response_model=list[schemas.FuelPriceOut], summary="Latest national fuel prices (US-09)")
def fuel_prices(fuel_type: schemas.FuelType = Query(...), service: ReferenceService = Depends(deps.reference_service)):
    """The newest weekly national average price of the chosen fuel in every country. Public."""
    return service.latest_prices(fuel_type)


@router.get(
    "/fuel-prices/{country}/trend",
    response_model=list[schemas.TrendPoint],
    responses={404: {"model": ErrorResponse}},
    summary="Price trend of up to 12 weeks (US-31)",
)
def fuel_price_trend(
    country: str, fuel_type: schemas.FuelType = Query(...), service: ReferenceService = Depends(deps.reference_service)
):
    """
    Weekly prices of the last 12 weeks for one country; `country` is an ISO code such as `PL`. 404 when
    there is no history.
    """
    return [schemas.TrendPoint(bulletin_date=d, price_eur_l=p) for d, p in service.price_trend(country, fuel_type)]


@router.get(
    "/speed-profiles",
    response_model=list[schemas.SpeedProfileOut],
    summary="Average speed and motorway limit of the current season (US-26)",
)
def speed_profiles(service: ReferenceService = Depends(deps.reference_service)):
    """
    Average driving speed and motorway limit of every country for the current season (winter: November-
    March). Public.
    """
    return service.speed_profiles()


@router.get(
    "/lodging-rates",
    response_model=list[schemas.LodgingRateOut],
    response_model_by_alias=True,
    summary="Typical nightly lodging prices (US-14)",
)
def lodging_rates(service: ReferenceService = Depends(deps.reference_service)):
    """Typical price per night by country and class. Public."""
    return service.lodging_rates()
